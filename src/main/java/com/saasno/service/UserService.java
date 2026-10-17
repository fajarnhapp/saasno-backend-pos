package com.saasno.service;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.saasno.entity.RoleEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.role.RoleDTO;
import com.saasno.utility.dto.user.UserDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.picker.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@Service
public class UserService implements UserDetailsService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private com.saasno.utility.mapper.UserMapper userMapper;

	@Autowired
	private BranchScopeService branchScopeService;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User tidak ditemukan");
		}

		return user.details();
	}

	@Transactional
	public ResponseModel<UserDTO> findByUsername() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		return new ResponseModel<>(ResponseModel.SUCCESS, toDto(user));
	}

	@Transactional
	public ResponseModel<UserDTO> create(UserDTO dto) {
		if (dto.getUsername() == null || dto.getUsername().isBlank()) {
			throw new ApiException("400", "Username tidak boleh kosong");
		}
		if (userRepository.findByUsername(dto.getUsername()) != null) {
			throw new ApiException("409", "Username sudah ada");
		}

		BranchScope scope = branchScopeService.current();
		UserEntity user = new UserEntity(dto.getUsername(), resolveRoles(dto.getRoles()));
		// User cabang hanya boleh membuat user di cabangnya atau turunannya, tidak pernah user global.
		// Hanya user global yang boleh membuat user tanpa cabang (akses penuh).
		boolean requestedBranch = dto.getBranchId() != null && !dto.getBranchId().isBlank();
		user.setBranchId(requestedBranch || !scope.isGlobal() ? branchScopeService.resolveOwner(scope, dto.getBranchId())
				: null);
		userRepository.store(user);
		return new ResponseModel<UserDTO>().success(toDto(user));
	}

	@Transactional
	public ResponseEntity<DataResult<UserDTO>> read(HttpServletRequest request) {
		try {
			BranchScope scope = branchScopeService.current();
			DataResult<UserEntity> result = userRepository.dataTable(scope.apply(new DataFilter(request), "branchId"));
			Map<String, String> branchNames = branchScopeService.names(result.getData().stream()
					.map(UserEntity::getBranchId).filter(id -> id != null).collect(Collectors.toSet()));
			List<UserDTO> dto = userMapper.dto(result.getData());
			dto.forEach(u -> u.setBranchName(branchNames.get(u.getBranchId())));
			return new ResponseEntity<DataResult<UserDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<UserDTO>>(new DataResult<UserDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<UserDTO> update(UserDTO dto) {
		BranchScope scope = branchScopeService.current();
		UserEntity existing = dto.getId() == null ? null : userRepository.find(dto.getId());
		if (existing == null || !scope.contains(existing.getBranchId())) {
			throw new ApiException("404", "User tidak ditemukan");
		}

		if (dto.getBranchId() != null) {
			boolean clear = dto.getBranchId().isBlank();
			String target = clear ? null : dto.getBranchId().trim();
			if (!java.util.Objects.equals(target, existing.getBranchId())) {
				// Tidak boleh mengubah cabang diri sendiri (mencegah naik ke global atau pindah ke luar).
				if (existing.getId().equals(sessionUtility.getUserId())) {
					throw new ApiException("403", "Tidak dapat mengubah cabang Anda sendiri");
				}
				// Hanya user global yang boleh mengosongkan cabang (menjadikan user global).
				if (clear && !scope.isGlobal()) {
					throw new ApiException("403", "Dilarang");
				}
				existing.setBranchId(clear ? null : branchScopeService.resolveOwner(scope, target));
			}
		}

		if (dto.getUsername() != null) {
			existing.setUsername(dto.getUsername());
		}
		if (dto.getRoles() != null) {
			existing.setRoles(resolveRoles(dto.getRoles()));
		}
		if (dto.getStatus() != null) {
			existing.setStatus(UserStatus.fromString(dto.getStatus()));
			// Admin yang menyimpan status user sekaligus membuka kunci percobaan login.
			existing.clearFailedLogin();
		}

		userRepository.update(existing);
		return new ResponseModel<UserDTO>().success(toDto(existing));
	}

	@Transactional
	public ResponseModel<UserDTO> delete(String id) {
		BranchScope scope = branchScopeService.current();
		UserEntity existing = id == null ? null : userRepository.find(id);
		if (existing == null || !scope.contains(existing.getBranchId())) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		if (userRepository.countStockActivity(existing.getId()) > 0) {
			// Riwayat stok mencatat pembuatnya; nonaktifkan user alih-alih menghapus.
			throw new ApiException("409", "User memiliki aktivitas stok, transaksi, keuangan, atau artikel sehingga tidak dapat dihapus; nonaktifkan saja");
		}
		userRepository.deleteNotifications(existing.getId());
		userRepository.delete(existing);
		return new ResponseModel<UserDTO>().success();
	}

	private Set<RoleEntity> resolveRoles(Set<RoleDTO> roles) {
		Set<RoleEntity> resolved = new HashSet<>();
		if (roles != null) {
			for (RoleDTO role : roles) {
				RoleEntity entity = roleRepository.find(role.getId());
				if (entity != null) {
					resolved.add(entity);
				}
			}
		}
		return resolved;
	}

	@Transactional
	public ResponseModel<UserDTO> resetPassword(String username) {
		BranchScope scope = branchScopeService.current();
		UserEntity user = userRepository.findByUsername(username);
		if (user == null || !scope.contains(user.getBranchId())) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		user.setPassword(UserEntity.passwordEncoder.encode(UserEntity.DEFAULT_PASSWORD));
		userRepository.store(user);
		return new ResponseModel<UserDTO>().success();
	}

	@Transactional
	public ResponseModel<UserDTO> changePassword(String password, String oldPassword) {
		String username = sessionUtility.getUsername();
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		if (!user.verifyPassword(oldPassword)) {
			throw new ApiException("400", "Kata sandi lama salah");
		}
		user.changePassword(password);
		userRepository.store(user);
		return new ResponseModel<>(ResponseModel.SUCCESS, userMapper.dto(user));
	}

	@Transactional
	public ResponseModel<Boolean> verifyPassword(String password) {
		String username = sessionUtility.getUsername();
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		if (!user.verifyPassword(password)) {
			throw new ApiException("400", "Kata sandi salah");
		}
		return new ResponseModel<Boolean>().success(true);
	}

	private UserDTO toDto(UserEntity user) {
		UserDTO dto = userMapper.dto(user);
		if (user.getBranchId() != null) {
			dto.setBranchName(branchScopeService.names(List.of(user.getBranchId())).get(user.getBranchId()));
		}
		return dto;
	}

}
