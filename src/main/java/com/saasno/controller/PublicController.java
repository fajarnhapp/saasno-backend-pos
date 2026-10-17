package com.saasno.controller;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.service.CatalogImageService;
import com.saasno.service.CatalogPublicService;
import com.saasno.service.OnlineOrderPublicService;
import com.saasno.utility.dto.order.PublicOrderDTO;
import com.saasno.utility.dto.order.PublicOrderRequest;
import com.saasno.utility.dto.catalog.PublicCatalogDTO;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.constant.Path;
import com.saasno.utility.model.ResponseModel;

// Data yang boleh dibaca tanpa login (halaman login). Hanya nilai yang memang ditujukan untuk umum;
// jangan menambahkan konfigurasi lain di sini tanpa dipikirkan.
@RestController
@RequestMapping(Path.API_PUBLIC)
public class PublicController {

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private CatalogPublicService catalogPublicService;

	@Autowired
	private CatalogImageService catalogImageService;

	@Autowired
	private OnlineOrderPublicService orderPublicService;

	// Pembeli membuat pesanan (tanpa akun). Harga dan stok dihitung server; yang dikembalikan adalah data pelacakan beserta tokennya.
	@PostMapping("/catalog/{slug}/order")
	public ResponseModel<PublicOrderDTO> order(@PathVariable("slug") String slug, @RequestBody PublicOrderRequest request) {
		return orderPublicService.place(slug, request);
	}

	// Status beberapa pesanan sekaligus (daftar "Pesanan saya" di browser pembeli); badan berisi {"tokens": [...]} maksimal 20.
	@PostMapping("/order/status")
	public ResponseModel<java.util.List<com.saasno.utility.dto.order.PublicOrderSummaryDTO>> statuses(@RequestBody java.util.Map<String, java.util.List<String>> request) {
		return orderPublicService.statuses(request == null ? null : request.get("tokens"));
	}

	// Mencari pesanan dengan nomor pesanan + nomor HP (keduanya harus cocok); mengembalikan token pelacakan.
	@PostMapping("/order/lookup")
	public ResponseModel<java.util.Map<String, String>> lookup(@RequestBody java.util.Map<String, String> request) {
		return orderPublicService.lookup(request == null ? null : request.get("serial"), request == null ? null : request.get("phone"));
	}

	// Pelacakan pesanan lewat token acak di tautan.
	@GetMapping("/order/{token}")
	public ResponseModel<PublicOrderDTO> track(@PathVariable("token") String token) {
		return orderPublicService.track(token);
	}

	@PostMapping("/order/{token}/cancel")
	public ResponseModel<PublicOrderDTO> cancelOrder(@PathVariable("token") String token) {
		return orderPublicService.cancel(token);
	}

	// Daftar katalog yang aktif; dipakai halaman /katalog bila ada lebih dari satu.
	@GetMapping("/catalog")
	public ResponseModel<java.util.List<PublicCatalogDTO.Entry>> catalogs() {
		return catalogPublicService.catalogs();
	}

	// Katalog produk sebuah cabang (alamat /katalog/<slug> di frontend). Tanpa login.
	@GetMapping("/catalog/{slug}")
	public ResponseModel<PublicCatalogDTO> catalog(@PathVariable("slug") String slug,
			@RequestParam(value = "q", required = false) String query,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return catalogPublicService.catalog(slug, query, offset, limit);
	}

	// Keadaan terkini beberapa produk sekaligus (mencocokkan keranjang pembeli); badan {"ids": [...]} maksimal 50.
	@PostMapping("/catalog/{slug}/products")
	public ResponseModel<java.util.List<PublicCatalogDTO.Product>> current(@PathVariable("slug") String slug,
			@RequestBody java.util.Map<String, java.util.List<String>> request) {
		return catalogPublicService.current(slug, request == null ? null : request.get("ids"));
	}

	// Foto produk katalog. ETag dari waktu penggantian foto; URL membawa ?v= sehingga boleh di-cache lama.
	@GetMapping("/catalog/{slug}/image/{productId}")
	public ResponseEntity<byte[]> image(@PathVariable("slug") String slug, @PathVariable("productId") String productId,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
		Long version = catalogPublicService.imageVersion(slug, productId);
		byte[] bytes = version == null ? null : catalogImageService.read(productId);
		if (bytes == null) {
			return ResponseEntity.notFound().build();
		}
		String etag = "\"" + version + "\"";
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
				.header("X-Content-Type-Options", "nosniff").body(bytes);
	}

	// Nomor WhatsApp admin untuk lupa kata sandi dan pendaftaran; kosong bila belum diatur.
	@GetMapping("/contact")
	public ResponseModel<Map<String, String>> contact() {
		Map<String, String> contact = new HashMap<>();
		String whatsapp = configurationRepository.get(ConfigurationKey.ADMIN_WHATSAPP);
		if (whatsapp != null && !whatsapp.isBlank()) {
			contact.put("whatsapp", whatsapp.trim());
		}
		return new ResponseModel<Map<String, String>>().success(contact);
	}

}
