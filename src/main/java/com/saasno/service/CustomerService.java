package com.saasno.service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.AddressEntity;
import com.saasno.entity.ContactEntity;
import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProfileEntity;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductCustomerPriceRepository;
import com.saasno.repository.PromoRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.customer.CustomerAddressDTO;
import com.saasno.utility.dto.customer.CustomerContactDTO;
import com.saasno.utility.dto.customer.CustomerDTO;
import com.saasno.utility.dto.customer.CustomerProfileDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.picker.AddressType;
import com.saasno.utility.picker.ContactType;
import com.saasno.utility.picker.CustomerStatus;
import com.saasno.utility.picker.CustomerType;
import com.saasno.utility.picker.Gender;
import com.saasno.utility.picker.Religion;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class CustomerService {

	private static final String COUNTRY = "INDONESIA";
	private static final int MAX_ADDRESSES = 10;
	private static final int MAX_CONTACTS = 20;
	private static final int MAX_EDUCATION = 9;

	private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-]{4,30}$");
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private ProductCustomerPriceRepository customerPriceRepository;

	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private com.saasno.repository.SaleRepository saleRepository;

	// Hasil validasi yang sudah dinormalisasi (enum sudah dicek, teks sudah dirapikan).
	private record Validated(String pic, CustomerType type, CustomerStatus status, ProfileEntity profile,
			List<AddressEntity> addresses, List<ContactEntity> contacts) {
	}

	@Transactional
	public ResponseModel<CustomerDTO> create(CustomerDTO request) {
		Validated v = validate(request);
		BranchScope scope = branchScopeService.current();

		CustomerEntity entity = new CustomerEntity();
		entity.setBranchId(branchScopeService.resolveOwner(scope, request.getBranchId()));
		entity.setPic(v.pic());
		entity.setType(v.type().name());
		entity.setStatus(v.status().name());
		entity.setProfile(v.profile());
		entity.getAddresses().addAll(v.addresses());
		entity.getContacts().addAll(v.contacts());
		customerRepository.store(entity);

		return new ResponseModel<CustomerDTO>().success(toDto(entity, branchScopeService.names(List.of(entity.getBranchId()))));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<CustomerDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<DataResult<CustomerDTO>>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<CustomerDTO>>(
					new DataResult<CustomerDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Dipakai read() dan export download.
	@Transactional(readOnly = true)
	public DataResult<CustomerDTO> find(DataFilter filter, BranchScope scope) {
		DataResult<CustomerEntity> result = customerRepository.dataTable(scope.apply(filter, "branchId"));
		Map<String, String> branchNames = branchScopeService
				.names(result.getData().stream().map(CustomerEntity::getBranchId).collect(Collectors.toSet()));
		List<CustomerDTO> dto = result.getData().stream().map(c -> toDto(c, branchNames))
				.collect(Collectors.toList());
		return new DataResult<>(dto, result.getTotal(), result.getGrandTotal());
	}

	// Update menggantikan seluruh data customer. Profile diubah di tempat; alamat dan kontak dicocokkan
	// per urutan: yang ada diubah, kelebihan dihapus, kekurangan ditambah.
	@Transactional
	public ResponseModel<CustomerDTO> update(CustomerDTO request) {
		BranchScope scope = branchScopeService.current();
		CustomerEntity entity = request.getId() == null ? null : customerRepository.find(request.getId());
		if (entity == null || !scope.contains(entity.getBranchId())) {
			throw new ApiException("404", "Customer tidak ditemukan");
		}
		branchScopeService.assertSameOwner(entity.getBranchId(), request.getBranchId());
		Validated v = validate(request);

		entity.setPic(v.pic());
		entity.setType(v.type().name());
		entity.setStatus(v.status().name());

		ProfileEntity profile = entity.getProfile();
		ProfileEntity next = v.profile();
		profile.setName(next.getName());
		profile.setGender(next.getGender());
		profile.setReligion(next.getReligion());
		profile.setBirthPlace(next.getBirthPlace());
		profile.setBirthDate(next.getBirthDate());
		profile.setEducation(next.getEducation());

		List<AddressEntity> addresses = entity.getAddresses();
		for (int i = 0; i < v.addresses().size(); i++) {
			AddressEntity incoming = v.addresses().get(i);
			if (i < addresses.size()) {
				AddressEntity current = addresses.get(i);
				current.setType(incoming.getType());
				current.setStreet(incoming.getStreet());
				current.setVillage(incoming.getVillage());
				current.setDistrict(incoming.getDistrict());
				current.setCity(incoming.getCity());
				current.setProvince(incoming.getProvince());
				current.setCountry(incoming.getCountry());
				current.setPostalCode(incoming.getPostalCode());
				current.setLatitude(incoming.getLatitude());
				current.setLongitude(incoming.getLongitude());
			} else {
				addresses.add(incoming);
			}
		}
		while (addresses.size() > v.addresses().size()) {
			addresses.remove(addresses.size() - 1);
		}

		List<ContactEntity> contacts = entity.getContacts();
		for (int i = 0; i < v.contacts().size(); i++) {
			ContactEntity incoming = v.contacts().get(i);
			if (i < contacts.size()) {
				ContactEntity current = contacts.get(i);
				current.setType(incoming.getType());
				current.setValue(incoming.getValue());
			} else {
				contacts.add(incoming);
			}
		}
		while (contacts.size() > v.contacts().size()) {
			contacts.remove(contacts.size() - 1);
		}

		customerRepository.update(entity);
		return new ResponseModel<CustomerDTO>().success(toDto(entity, branchScopeService.names(List.of(entity.getBranchId()))));
	}

	@Transactional
	public ResponseModel<CustomerDTO> delete(String id) {
		BranchScope scope = branchScopeService.current();
		CustomerEntity entity = id == null ? null : customerRepository.find(id);
		if (entity == null || !scope.contains(entity.getBranchId())) {
			throw new ApiException("404", "Customer tidak ditemukan");
		}
		if (customerPriceRepository.countByCustomer(id) > 0) {
			throw new ApiException("409", "Customer masih dipakai pada harga produk");
		}
		if (saleRepository.countByCustomer(id) > 0) {
			throw new ApiException("409", "Customer masih dipakai pada transaksi");
		}
		if (promoRepository.countByCustomer(id) > 0) {
			throw new ApiException("409", "Customer masih dipakai pada promo");
		}
		customerRepository.delete(entity);
		return new ResponseModel<CustomerDTO>().success();
	}

	private Validated validate(CustomerDTO r) {
		String pic = clean(r.getPic());
		if (pic == null) {
			throw new ApiException("400", "PIC tidak boleh kosong");
		}
		maxLength("PIC", pic, 128);

		CustomerType type = CustomerType.fromString(r.getType());
		CustomerStatus status = r.getStatus() == null || r.getStatus().isBlank() ? CustomerStatus.ACTIVE
				: CustomerStatus.fromString(r.getStatus().trim());

		ProfileEntity profile = profile(r.getProfile(), type);

		List<CustomerAddressDTO> addressRequests = r.getAddresses() == null ? List.of() : r.getAddresses();
		if (addressRequests.size() > MAX_ADDRESSES) {
			throw new ApiException("400", "Alamat tidak boleh lebih dari " + MAX_ADDRESSES);
		}
		List<AddressEntity> addresses = new ArrayList<>();
		for (CustomerAddressDTO a : addressRequests) {
			addresses.add(address(a));
		}

		List<CustomerContactDTO> contactRequests = r.getContacts() == null ? List.of() : r.getContacts();
		if (contactRequests.size() > MAX_CONTACTS) {
			throw new ApiException("400", "Kontak tidak boleh lebih dari " + MAX_CONTACTS);
		}
		List<ContactEntity> contacts = new ArrayList<>();
		for (CustomerContactDTO c : contactRequests) {
			contacts.add(contact(c));
		}
		return new Validated(pic, type, status, profile, addresses, contacts);
	}

	// Nama wajib. Field pribadi hanya berlaku untuk INDIVIDU; untuk BISNIS dikosongkan.
	private ProfileEntity profile(CustomerProfileDTO p, CustomerType type) {
		String name = p == null ? null : clean(p.getName());
		if (name == null) {
			throw new ApiException("400", "Nama tidak boleh kosong");
		}
		maxLength("Nama", name, 128);

		ProfileEntity profile = new ProfileEntity();
		profile.setName(name);
		if (type == CustomerType.INDIVIDU) {
			if (clean(p.getGender()) != null) {
				profile.setGender(Gender.fromString(p.getGender().trim()));
			}
			if (clean(p.getReligion()) != null) {
				profile.setReligion(Religion.fromString(p.getReligion().trim()));
			}
			String birthPlace = clean(p.getBirthPlace());
			maxLength("Tempat lahir", birthPlace, 128);
			profile.setBirthPlace(birthPlace);
			if (p.getBirthDate() != null && p.getBirthDate().isAfter(LocalDate.now())) {
				throw new ApiException("400", "Tanggal lahir tidak boleh di masa depan");
			}
			profile.setBirthDate(p.getBirthDate());
			if (p.getEducation() != null && (p.getEducation() < 0 || p.getEducation() > MAX_EDUCATION)) {
				throw new ApiException("400", "Pendidikan tidak valid");
			}
			profile.setEducation(p.getEducation());
		}
		return profile;
	}

	// Jalan, provinsi, dan kota wajib; negara selalu INDONESIA.
	private AddressEntity address(CustomerAddressDTO a) {
		if (a == null) {
			throw new ApiException("400", "Alamat tidak valid");
		}
		AddressType type = AddressType.fromString(a.getType());
		String street = clean(a.getStreet());
		String province = clean(a.getProvince());
		String city = clean(a.getCity());
		if (street == null) {
			throw new ApiException("400", "Jalan tidak boleh kosong");
		}
		if (province == null) {
			throw new ApiException("400", "Provinsi tidak boleh kosong");
		}
		if (city == null) {
			throw new ApiException("400", "Kota tidak boleh kosong");
		}
		maxLength("Jalan", street, 2024);
		maxLength("Kelurahan/Desa", clean(a.getVillage()), 128);
		maxLength("Kecamatan", clean(a.getDistrict()), 128);
		maxLength("Kota", city, 128);
		maxLength("Provinsi", province, 128);
		maxLength("Kode pos", clean(a.getPostalCode()), 8);
		coordinate("Lintang", clean(a.getLatitude()), 90);
		coordinate("Bujur", clean(a.getLongitude()), 180);

		AddressEntity address = new AddressEntity();
		address.setId(ID.uuid());
		address.setType(type.name());
		address.setStreet(street);
		address.setVillage(clean(a.getVillage()));
		address.setDistrict(clean(a.getDistrict()));
		address.setCity(city);
		address.setProvince(province);
		address.setCountry(COUNTRY);
		address.setPostalCode(clean(a.getPostalCode()));
		address.setLatitude(clean(a.getLatitude()));
		address.setLongitude(clean(a.getLongitude()));
		return address;
	}

	private ContactEntity contact(CustomerContactDTO c) {
		if (c == null) {
			throw new ApiException("400", "Kontak tidak valid");
		}
		ContactType type = ContactType.fromString(c.getType());
		String value = clean(c.getValue());
		if (value == null) {
			throw new ApiException("400", "Nilai kontak tidak boleh kosong");
		}
		maxLength("Nilai kontak", value, 256);
		switch (type) {
		case EMAIL -> {
			if (!EMAIL.matcher(value).matches()) {
				throw new ApiException("400", "Email tidak valid");
			}
		}
		case PHONE, EMERGENCY_PHONE, WHATSAPP -> {
			if (!PHONE.matcher(value).matches()) {
				throw new ApiException("400", "Telepon tidak valid");
			}
		}
		default -> {
		}
		}
		ContactEntity contact = new ContactEntity();
		contact.setId(ID.uuid());
		contact.setType(type.name());
		contact.setValue(value);
		return contact;
	}

	private CustomerDTO toDto(CustomerEntity e, Map<String, String> branchNames) {
		CustomerDTO dto = new CustomerDTO();
		dto.setId(e.getId());
		dto.setBranchId(e.getBranchId());
		dto.setBranchName(branchNames.get(e.getBranchId()));
		dto.setSerial(e.getSerial());
		dto.setPic(e.getPic());
		dto.setType(e.getType());
		dto.setStatus(e.getStatus());
		dto.setModificated(e.getModificated());
		dto.setRegistered(e.getRegistered());

		ProfileEntity p = e.getProfile();
		CustomerProfileDTO profile = new CustomerProfileDTO();
		profile.setName(p.getName());
		profile.setGender(p.getGender() == null ? null : p.getGender().name());
		profile.setReligion(p.getReligion() == null ? null : p.getReligion().name());
		profile.setBirthPlace(p.getBirthPlace());
		profile.setBirthDate(p.getBirthDate());
		profile.setEducation(p.getEducation());
		dto.setProfile(profile);

		dto.setAddresses(e.getAddresses().stream().map(a -> {
			CustomerAddressDTO d = new CustomerAddressDTO();
			d.setType(a.getType());
			d.setStreet(a.getStreet());
			d.setVillage(a.getVillage());
			d.setDistrict(a.getDistrict());
			d.setCity(a.getCity());
			d.setProvince(a.getProvince());
			d.setCountry(a.getCountry());
			d.setPostalCode(a.getPostalCode());
			d.setLatitude(a.getLatitude());
			d.setLongitude(a.getLongitude());
			return d;
		}).collect(Collectors.toList()));

		dto.setContacts(e.getContacts().stream().map(c -> {
			CustomerContactDTO d = new CustomerContactDTO();
			d.setType(c.getType());
			d.setValue(c.getValue());
			return d;
		}).collect(Collectors.toList()));
		return dto;
	}

	private void maxLength(String field, String value, int max) {
		if (value != null && value.length() > max) {
			throw new ApiException("400", field + " tidak boleh lebih dari " + max + " karakter");
		}
	}

	private void coordinate(String field, String value, double limit) {
		if (value == null) {
			return;
		}
		try {
			double number = Double.parseDouble(value);
			if (Double.isNaN(number) || Math.abs(number) > limit) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException ex) {
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
