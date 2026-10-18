package com.saasno.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.session.SessionDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

// Daftar perangkat yang sedang login dan pengeluaran paksa. User melihat dan mengeluarkan perangkat lainnya sendiri; peran dengan
// izin "sesi semua" melakukannya untuk user dalam cakupan cabangnya. Sesi yang sedang dipakai tidak bisa dikeluarkan di sini
// (gunakan Keluar), dan id sesi (token) tidak pernah dikirim: hanya pegangan hash-nya.
@Service
public class SessionManagementService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private UserRepository userRepository;

	public ResponseModel<List<SessionDTO>> mine() {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		List<SessionDTO> list = new ArrayList<>();
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId())) {
				list.add(dto(s, current, null));
			}
		}
		list.sort(Comparator.comparing(SessionDTO::registered).reversed());
		return new ResponseModel<List<SessionDTO>>().success(list);
	}

	public ResponseModel<Void> revokeMine(String handle) {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		if (handle == null || handle.equals(current)) {
			throw new ApiException("409", "Ini perangkat yang sedang Anda pakai; gunakan tombol Keluar");
		}
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId()) && SessionService.handle(s).equals(handle)) {
				sessionService.delete(s.getId());
				return new ResponseModel<Void>().success(null);
			}
		}
		throw new ApiException("404", "Sesi tidak ditemukan");
	}

	// Mengeluarkan semua perangkat lain milik sendiri.
	public ResponseModel<Integer> revokeOthers() {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		int count = 0;
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId()) && !SessionService.handle(s).equals(current)) {
				sessionService.delete(s.getId());
				count++;
			}
		}
		return new ResponseModel<Integer>().success(count);
	}

	public ResponseModel<List<SessionDTO>> all() {
		BranchScope scope = branchScopeService.current();
		String current = SessionService.handle(sessionUtility.get());
		List<SessionDTO> list = new ArrayList<>();
		Map<String, UserEntity> users = new HashMap<>();
		Set<String> branchIds = new HashSet<>();
		List<SessionEntity> inScope = new ArrayList<>();
		for (SessionEntity s : sessionService.readAll()) {
			if (s.getUserId() == null) {
				continue;
			}
			UserEntity user = users.computeIfAbsent(s.getUserId(), id -> userRepository.find(id));
			if (user != null && scope.contains(user.getBranchId())) {
				inScope.add(s);
				if (user.getBranchId() != null) {
					branchIds.add(user.getBranchId());
				}
			}
		}
		Map<String, String> names = branchIds.isEmpty() ? Map.of() : branchScopeService.names(branchIds);
		for (SessionEntity s : inScope) {
			list.add(dto(s, current, names.get(users.get(s.getUserId()).getBranchId())));
		}
		list.sort(Comparator.comparing(SessionDTO::username).thenComparing(SessionDTO::registered, Comparator.reverseOrder()));
		return new ResponseModel<List<SessionDTO>>().success(list);
	}

	public ResponseModel<Void> revokeAny(String handle) {
		BranchScope scope = branchScopeService.current();
		String current = SessionService.handle(sessionUtility.get());
		if (handle == null || handle.equals(current)) {
			throw new ApiException("409", "Ini perangkat yang sedang Anda pakai; gunakan tombol Keluar");
		}
		for (SessionEntity s : sessionService.readAll()) {
			if (s.getUserId() != null && SessionService.handle(s).equals(handle)) {
				UserEntity user = userRepository.find(s.getUserId());
				if (user == null || !scope.contains(user.getBranchId())) {
					break;
				}
				sessionService.delete(s.getId());
				return new ResponseModel<Void>().success(null);
			}
		}
		throw new ApiException("404", "Sesi tidak ditemukan");
	}

	private SessionDTO dto(SessionEntity s, String current, String branchName) {
		return new SessionDTO(SessionService.handle(s), s.getUserId(), s.getUsername(), branchName, s.getIp(), s.getUserAgent(), s.getRegistered(),
				s.getExpired(), SessionService.handle(s).equals(current));
	}
}
