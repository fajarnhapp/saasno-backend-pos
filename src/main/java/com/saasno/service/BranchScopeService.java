package com.saasno.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.exception.ApiException;

// Menentukan cabang apa saja yang boleh dilihat/diubah user. Dihitung dari DB di setiap pemanggilan
// (bukan dari session Redis) supaya perubahan cabang user atau pohon cabang langsung berlaku.
@Service
public class BranchScopeService {

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchRepository branchRepository;

	// Scope user yang sedang login. Tanpa session yang valid ditolak, tidak pernah jatuh ke global.
	@Transactional(readOnly = true)
	public BranchScope current() {
		String userId = sessionUtility.getUserId();
		if (userId == null) {
			throw new ApiException("403", "Dilarang");
		}
		return forUser(userId);
	}

	// Dipakai juga oleh job async (download) yang tidak punya session, lewat userId pemintanya.
	@Transactional(readOnly = true)
	public BranchScope forUser(String userId) {
		UserEntity user = userId == null ? null : userRepository.find(userId);
		if (user == null) {
			throw new ApiException("403", "Dilarang");
		}
		return subtree(user.getBranchId(), true);
	}

	// Cabang ini beserta seluruh turunannya. Dipakai untuk memvalidasi relasi antar data: data milik
	// cabang X hanya boleh merujuk data milik X atau turunan X, supaya siapa pun yang bisa melihat
	// data X juga bisa melihat apa yang dirujuknya.
	@Transactional(readOnly = true)
	public BranchScope subtree(String branchId) {
		return subtree(branchId, false);
	}

	private BranchScope subtree(String branchId, boolean nullIsGlobal) {
		if (branchId == null) {
			if (nullIsGlobal) {
				return BranchScope.global();
			}
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		Set<String> ids = branchRepository.findSubtreeIds(branchId);
		if (!ids.contains(branchId)) {
			// Cabang user sudah tidak ada: jangan pernah menganggapnya global.
			throw new ApiException("403", "Dilarang");
		}
		return BranchScope.of(branchId, ids, branchRepository.findAncestorIds(branchId));
	}

	// Cabang pemilik data baru. Kalau tidak dikirim, user cabang memakai cabangnya sendiri; user global
	// wajib memilih. Cabang di luar cakupan diperlakukan seperti tidak ada.
	@Transactional(readOnly = true)
	public String resolveOwner(BranchScope scope, String requested) {
		String branchId = requested == null || requested.isBlank() ? null : requested.trim();
		if (branchId == null) {
			if (scope.isGlobal()) {
				throw new ApiException("400", "Cabang tidak boleh kosong");
			}
			branchId = scope.getBranchId();
		}
		if (!scope.contains(branchId) || branchRepository.find(branchId) == null) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		return branchId;
	}

	// Cabang tempat sebuah tindakan dicatat (transaksi, biaya, kas kecil): user cabang selalu cabangnya sendiri (ditolak 403
	// bila menyebut yang lain); user global harus menyebutkannya.
	@Transactional(readOnly = true)
	public BranchEntity ownBranch(BranchScope scope, String requested, String forbiddenMessage) {
		String id = requested == null || requested.isBlank() ? null : requested.trim();
		if (!scope.isGlobal()) {
			if (id != null && !id.equals(scope.getBranchId())) {
				throw new ApiException("403", forbiddenMessage);
			}
			id = scope.getBranchId();
		}
		if (id == null) {
			throw new ApiException("400", "Cabang tidak boleh kosong");
		}
		BranchEntity branch = branchRepository.find(id);
		if (branch == null) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		return branch;
	}

	// Cabang pemilik tidak boleh dipindah setelah dibuat (relasi antar data bergantung padanya).
	public void assertSameOwner(String current, String requested) {
		if (requested != null && !requested.isBlank() && !requested.trim().equals(current)) {
			throw new ApiException("400", "Cabang tidak dapat diubah");
		}
	}

	@Transactional(readOnly = true)
	public Map<String, String> names(Collection<String> branchIds) {
		Map<String, String> names = new HashMap<>();
		for (BranchEntity branch : branchRepository.findByIds(branchIds)) {
			names.put(branch.getId(), branch.getName());
		}
		return names;
	}

}
