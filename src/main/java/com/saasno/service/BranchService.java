package com.saasno.service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.AddressEntity;
import com.saasno.entity.BranchAddressEntity;
import com.saasno.entity.BranchContactEntity;
import com.saasno.entity.BranchEntity;
import com.saasno.entity.ContactEntity;
import com.saasno.repository.AddressRepository;
import com.saasno.repository.BranchAddressRepository;
import com.saasno.repository.BranchContactRepository;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ContactRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.branch.BranchDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class BranchService {

	// Tipe ditentukan sistem, bukan input user.
	private static final String ADDRESS_TYPE = "OFFICE";
	private static final String ADDRESS_COUNTRY = "INDONESIA";
	private static final String CONTACT_PHONE = "PHONE";
	private static final String CONTACT_EMAIL = "EMAIL";

	private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-]{4,30}$");
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private ContactRepository contactRepository;

	@Autowired
	private BranchAddressRepository branchAddressRepository;

	@Autowired
	private BranchContactRepository branchContactRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional
	public ResponseModel<BranchDTO> create(BranchDTO request) {
		validate(request);

		String name = clean(request.getName());
		if (name == null) {
			throw new ApiException("400", "Nama tidak boleh kosong");
		}
		BranchScope scope = branchScopeService.current();
		String parentId = clean(request.getParentId());
		if (parentId == null && !scope.isGlobal()) {
			// User cabang tidak boleh membuat cabang tanpa induk; default-nya di bawah cabangnya sendiri.
			parentId = scope.getBranchId();
		}
		// Induk di luar cakupan diperlakukan seperti tidak ada, supaya keberadaannya tidak bocor.
		if (parentId != null && (!scope.contains(parentId) || branchRepository.find(parentId) == null)) {
			throw new ApiException("404", "Cabang induk tidak ditemukan");
		}

		BranchEntity branch = new BranchEntity(name, parentId);
		branchRepository.store(branch);

		applyAddress(branch, request, null);
		applyContact(branch, CONTACT_PHONE, request.getPhone(), null);
		applyContact(branch, CONTACT_EMAIL, request.getEmail(), null);

		return new ResponseModel<BranchDTO>().success(toDto(branch.getId(), scope));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<BranchDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<DataResult<BranchDTO>>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<BranchDTO>>(
					new DataResult<BranchDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Dipakai read() dan export download: data cabang lengkap dengan alamat, kontak, dan nama induk.
	@Transactional(readOnly = true)
	public DataResult<BranchDTO> find(DataFilter filter, BranchScope scope) {
		DataResult<BranchEntity> result = branchRepository.dataTable(scope.apply(filter, "id"));
		List<BranchEntity> branches = result.getData();

		Set<String> ids = branches.stream().map(BranchEntity::getId).collect(Collectors.toSet());
		Set<String> parentIds = branches.stream().map(BranchEntity::getParentId)
				.filter(id -> id != null).collect(Collectors.toSet());

		Map<String, AddressEntity> addresses = new HashMap<>();
		for (BranchAddressEntity link : branchAddressRepository.findByBranchIds(ids)) {
			addresses.put(link.getBranch().getId(), link.getAddress());
		}
		Map<String, Map<String, ContactEntity>> contacts = new HashMap<>();
		for (BranchContactEntity link : branchContactRepository.findByBranchIds(ids)) {
			contacts.computeIfAbsent(link.getBranch().getId(), k -> new HashMap<>())
					.put(link.getContact().getType(), link.getContact());
		}
		Map<String, String> parentNames = new HashMap<>();
		for (BranchEntity parent : branchRepository.findByIds(parentIds)) {
			parentNames.put(parent.getId(), parent.getName());
		}

		List<BranchDTO> dto = branches.stream()
				.map(b -> toDto(b, addresses.get(b.getId()), contacts.get(b.getId()),
						parentNames.get(b.getParentId()), scope))
				.collect(Collectors.toList());
		return new DataResult<>(dto, result.getTotal(), result.getGrandTotal());
	}

	@Transactional
	public ResponseModel<BranchDTO> update(BranchDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = request.getId() == null ? null : branchRepository.find(request.getId());
		if (branch == null || !scope.contains(branch.getId())) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		validate(request);

		// Field yang tidak dikirim (null) dianggap tidak berubah.
		if (request.getName() != null) {
			String name = clean(request.getName());
			if (name == null) {
				throw new ApiException("400", "Nama tidak boleh kosong");
			}
			branch.setName(name);
		}

		// User cabang tidak boleh memindahkan cabangnya sendiri (induknya di luar cakupannya), jadi
		// parentId untuk cabangnya sendiri diabaikan.
		boolean ownBranch = !scope.isGlobal() && branch.getId().equals(scope.getBranchId());
		if (request.getParentId() != null && !ownBranch) {
			String parentId = clean(request.getParentId());
			if (!scope.isGlobal() && (parentId == null || !scope.contains(parentId))) {
				throw new ApiException("404", "Cabang induk tidak ditemukan");
			}
			if (parentId != null) {
				assertValidParent(branch.getId(), parentId);
			}
			branch.setParentId(parentId);
		}
		branchRepository.update(branch);

		List<BranchAddressEntity> addressLinks = branchAddressRepository.findByBranchIds(List.of(branch.getId()));
		applyAddress(branch, request, addressLinks.isEmpty() ? null : addressLinks.get(0));

		Map<String, BranchContactEntity> contactLinks = new HashMap<>();
		for (BranchContactEntity link : branchContactRepository.findByBranchIds(List.of(branch.getId()))) {
			contactLinks.put(link.getContact().getType(), link);
		}
		applyContact(branch, CONTACT_PHONE, request.getPhone(), contactLinks.get(CONTACT_PHONE));
		applyContact(branch, CONTACT_EMAIL, request.getEmail(), contactLinks.get(CONTACT_EMAIL));

		return new ResponseModel<BranchDTO>().success(toDto(branch.getId(), scope));
	}

	@Transactional
	public ResponseModel<BranchDTO> delete(String id) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = id == null ? null : branchRepository.find(id);
		if (branch == null || !scope.contains(branch.getId())) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		if (!scope.isGlobal() && branch.getId().equals(scope.getBranchId())) {
			throw new ApiException("403", "Tidak dapat menghapus cabang Anda sendiri");
		}
		if (branchRepository.countChildren(id) > 0) {
			throw new ApiException("409", "Cabang masih memiliki cabang turunan");
		}
		if (branchRepository.countOwnedData(id) > 0) {
			throw new ApiException("409", "Cabang masih dipakai oleh user, produk, promo, customer, stok, transaksi, keuangan, atau artikel");
		}

		List<BranchAddressEntity> addressLinks = branchAddressRepository.findByBranchIds(List.of(id));
		List<BranchContactEntity> contactLinks = branchContactRepository.findByBranchIds(List.of(id));
		for (BranchAddressEntity link : addressLinks) {
			branchAddressRepository.delete(link);
		}
		for (BranchContactEntity link : contactLinks) {
			branchContactRepository.delete(link);
		}
		branchRepository.delete(branch);
		for (BranchAddressEntity link : addressLinks) {
			addressRepository.delete(link.getAddress());
		}
		for (BranchContactEntity link : contactLinks) {
			contactRepository.delete(link.getContact());
		}
		return new ResponseModel<BranchDTO>().success();
	}

	// Parent harus ada, bukan dirinya sendiri, dan bukan salah satu turunannya (mencegah siklus).
	private void assertValidParent(String branchId, String parentId) {
		if (parentId.equals(branchId)) {
			throw new ApiException("400", "Cabang tidak boleh menjadi induk dirinya sendiri");
		}
		Set<String> visited = new HashSet<>();
		String current = parentId;
		while (current != null && visited.add(current)) {
			BranchEntity ancestor = branchRepository.find(current);
			if (ancestor == null) {
				throw new ApiException("404", "Cabang induk tidak ditemukan");
			}
			if (branchId.equals(ancestor.getParentId())) {
				throw new ApiException("400", "Cabang induk tidak boleh turunan dari cabang ini");
			}
			current = ancestor.getParentId();
		}
	}

	private void applyAddress(BranchEntity branch, BranchDTO request, BranchAddressEntity link) {
		boolean provided = request.getStreet() != null || request.getVillage() != null
				|| request.getDistrict() != null || request.getCity() != null || request.getProvince() != null
				|| request.getPostalCode() != null
				|| request.getLatitude() != null || request.getLongitude() != null;
		if (!provided) {
			return;
		}

		AddressEntity address = link != null ? link.getAddress() : new AddressEntity();
		if (request.getStreet() != null) address.setStreet(clean(request.getStreet()));
		if (request.getVillage() != null) address.setVillage(clean(request.getVillage()));
		if (request.getDistrict() != null) address.setDistrict(clean(request.getDistrict()));
		if (request.getCity() != null) address.setCity(clean(request.getCity()));
		if (request.getProvince() != null) address.setProvince(clean(request.getProvince()));
		if (request.getPostalCode() != null) address.setPostalCode(clean(request.getPostalCode()));
		if (request.getLatitude() != null) address.setLatitude(clean(request.getLatitude()));
		if (request.getLongitude() != null) address.setLongitude(clean(request.getLongitude()));

		// Negara selalu diisi sistem, jadi tidak ikut menentukan alamat kosong atau tidak.
		boolean empty = address.getStreet() == null && address.getVillage() == null
				&& address.getDistrict() == null && address.getCity() == null && address.getProvince() == null
				&& address.getPostalCode() == null
				&& address.getLatitude() == null && address.getLongitude() == null;

		if (link == null) {
			if (empty) {
				return;
			}
			address.setId(ID.uuid());
			address.setType(ADDRESS_TYPE);
			address.setCountry(ADDRESS_COUNTRY);
			addressRepository.store(address);
			BranchAddressEntity newLink = new BranchAddressEntity();
			newLink.setBranch(branch);
			newLink.setAddress(address);
			branchAddressRepository.store(newLink);
		} else if (empty) {
			branchAddressRepository.delete(link);
			addressRepository.delete(address);
		} else {
			address.setCountry(ADDRESS_COUNTRY);
			addressRepository.update(address);
		}
	}

	private void applyContact(BranchEntity branch, String type, String rawValue, BranchContactEntity link) {
		if (rawValue == null) {
			return;
		}
		String value = clean(rawValue);

		if (link == null) {
			if (value == null) {
				return;
			}
			ContactEntity contact = new ContactEntity();
			contact.setId(ID.uuid());
			contact.setType(type);
			contact.setValue(value);
			contactRepository.store(contact);
			BranchContactEntity newLink = new BranchContactEntity();
			newLink.setBranch(branch);
			newLink.setContact(contact);
			branchContactRepository.store(newLink);
		} else if (value == null) {
			branchContactRepository.delete(link);
			contactRepository.delete(link.getContact());
		} else {
			ContactEntity contact = link.getContact();
			contact.setValue(value);
			contactRepository.update(contact);
		}
	}

	private BranchDTO toDto(String branchId, BranchScope scope) {
		BranchEntity branch = branchRepository.find(branchId);
		AddressEntity address = null;
		for (BranchAddressEntity link : branchAddressRepository.findByBranchIds(List.of(branchId))) {
			address = link.getAddress();
		}
		Map<String, ContactEntity> contacts = new HashMap<>();
		for (BranchContactEntity link : branchContactRepository.findByBranchIds(List.of(branchId))) {
			contacts.put(link.getContact().getType(), link.getContact());
		}
		BranchEntity parent = branch.getParentId() == null ? null : branchRepository.find(branch.getParentId());
		return toDto(branch, address, contacts, parent == null ? null : parent.getName(), scope);
	}

	private BranchDTO toDto(BranchEntity branch, AddressEntity address, Map<String, ContactEntity> contacts,
			String parentName, BranchScope scope) {
		BranchDTO dto = new BranchDTO();
		dto.setId(branch.getId());
		dto.setSerial(branch.getSerial());
		dto.setName(branch.getName());
		// Induk di atas cakupan user tidak ditampilkan.
		boolean parentVisible = scope.contains(branch.getParentId());
		dto.setParentId(parentVisible ? branch.getParentId() : null);
		dto.setParentName(parentVisible ? parentName : null);
		dto.setModificated(branch.getModificated());
		dto.setRegistered(branch.getRegistered());
		if (address != null) {
			dto.setStreet(address.getStreet());
			dto.setVillage(address.getVillage());
			dto.setDistrict(address.getDistrict());
			dto.setCity(address.getCity());
			dto.setProvince(address.getProvince());
			dto.setCountry(address.getCountry());
			dto.setPostalCode(address.getPostalCode());
			dto.setLatitude(address.getLatitude());
			dto.setLongitude(address.getLongitude());
		}
		if (contacts != null) {
			ContactEntity phone = contacts.get(CONTACT_PHONE);
			ContactEntity email = contacts.get(CONTACT_EMAIL);
			dto.setPhone(phone == null ? null : phone.getValue());
			dto.setEmail(email == null ? null : email.getValue());
		}
		return dto;
	}

	private void validate(BranchDTO r) {
		maxLength("Nama", r.getName(), 256);
		maxLength("Jalan", r.getStreet(), 2024);
		maxLength("Kelurahan/Desa", r.getVillage(), 128);
		maxLength("Kecamatan", r.getDistrict(), 128);
		maxLength("Kota", r.getCity(), 128);
		maxLength("Provinsi", r.getProvince(), 128);
		maxLength("Kode pos", r.getPostalCode(), 8);

		String phone = clean(r.getPhone());
		if (phone != null && !PHONE.matcher(phone).matches()) {
			throw new ApiException("400", "Telepon tidak valid");
		}
		String email = clean(r.getEmail());
		if (email != null && (email.length() > 256 || !EMAIL.matcher(email).matches())) {
			throw new ApiException("400", "Email tidak valid");
		}
		coordinate("Lintang", r.getLatitude(), 90);
		coordinate("Bujur", r.getLongitude(), 180);
	}

	private void maxLength(String field, String value, int max) {
		String v = clean(value);
		if (v != null && v.length() > max) {
			throw new ApiException("400", field + " tidak boleh lebih dari " + max + " karakter");
		}
	}

	private void coordinate(String field, String raw, double limit) {
		String value = clean(raw);
		if (value == null) {
			return;
		}
		try {
			double number = Double.parseDouble(value);
			if (Double.isNaN(number) || Math.abs(number) > limit) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException e) {
			throw new ApiException("400", field + " tidak valid");
		}
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
