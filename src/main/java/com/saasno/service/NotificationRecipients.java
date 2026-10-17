package com.saasno.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.BranchRepository;
import com.saasno.repository.UserRepository;

import jakarta.persistence.EntityManager;

// Menentukan siapa yang menerima sebuah notifikasi dari izin API dan cabang, sama seperti dashboard: user aktif
// yang perannya punya izin tersebut dan cakupan cabangnya mencakup cabang kejadian (user global selalu mencakup).
@Service
public class NotificationRecipients {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private UserRepository userRepository;

	// User yang punya izin apiId dan berhak atas cabang branchId (cabang itu sendiri, induknya, atau user global).
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public Set<String> withApi(String apiId, String branchId) {
		Set<String> ancestors = branchRepository.findAncestorIds(branchId);
		if (ancestors.isEmpty()) {
			return new HashSet<>();
		}
		List<String> ids = entityManager.createNativeQuery("SELECT DISTINCT u.id FROM public.\"user\" u "
				+ "JOIN public.user_role ur ON ur.user_id = u.id "
				+ "JOIN public.role_api ra ON ra.role_id = ur.role_id "
				+ "WHERE ra.api_id = :api AND u.status = 'ACTIVED' AND (u.branch_id IS NULL OR u.branch_id IN (:branches))")
				.setParameter("api", apiId).setParameter("branches", ancestors).getResultList();
		return new HashSet<>(ids);
	}

	// Semua user aktif yang ditempatkan di cabang ini atau turunannya.
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public Set<String> inSubtree(String branchId) {
		Set<String> subtree = branchRepository.findSubtreeIds(branchId);
		if (subtree.isEmpty()) {
			return new HashSet<>();
		}
		List<String> ids = entityManager.createNativeQuery(
				"SELECT u.id FROM public.\"user\" u WHERE u.status = 'ACTIVED' AND u.branch_id IN (:branches)")
				.setParameter("branches", subtree).getResultList();
		return new HashSet<>(ids);
	}

	// Nominal rupiah untuk dicantumkan di pesan, contoh: Rp 1.250.000.
	public String rupiah(java.math.BigDecimal value) {
		java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0",
				java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag("id-ID")));
		return "Rp " + format.format(value == null ? java.math.BigDecimal.ZERO : value);
	}

	// Nama pengguna untuk dicantumkan di pesan.
	@Transactional(readOnly = true)
	public String username(String userId) {
		if (userId == null) {
			return "-";
		}
		Map<String, String> names = userRepository.findUsernames(new ArrayList<>(List.of(userId)));
		return names.getOrDefault(userId, "-");
	}

}
