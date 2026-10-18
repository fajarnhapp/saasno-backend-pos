package com.saasno.utility.dto.purchase;

// Supplier. Pada permintaan, hanya name, contactName, phone, address, note, status, dan branchId (pemilik, saat dibuat) dipakai.
public record SupplierDTO(String id, String serial, String branchId, String branchName, String name, String contactName,
		String phone, String address, String note, String status) {
}
