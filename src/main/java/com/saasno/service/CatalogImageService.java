package com.saasno.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;

// Foto produk katalog. Berkas yang diunggah TIDAK disimpan apa adanya: dibaca sebagai gambar (JPEG atau PNG saja), diperkecil
// ke sisi terpanjang 800 px, lalu ditulis ulang sebagai JPEG. Dengan begitu isinya pasti gambar, metadata (mis. lokasi GPS dari
// kamera) terbuang, dan ukurannya kecil. Nama berkas diturunkan dari id produk, tidak pernah dari nama unggahan.
@Service
public class CatalogImageService {

	private static final int MAX_SIDE = 800;
	private static final long MAX_PIXELS = 40_000_000L;
	private static final float QUALITY = 0.82f;

	@Value("${app.upload.dir}")
	private String uploadDir;

	public byte[] normalize(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException("400", "Foto tidak boleh kosong");
		}
		try (InputStream in = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw new ApiException("400", "Foto harus berupa gambar JPEG atau PNG");
			}
			ImageReader reader = readers.next();
			try {
				String format = reader.getFormatName().toLowerCase();
				if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
					throw new ApiException("400", "Foto harus berupa gambar JPEG atau PNG");
				}
				reader.setInput(stream, true, true);
				// Ukuran dicek sebelum gambar didekode, supaya gambar raksasa tidak menghabiskan memori.
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw new ApiException("400", "Resolusi foto terlalu besar");
				}
				return encode(reader.read(0));
			} finally {
				reader.dispose();
			}
		} catch (ApiException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			Log.error(e.getMessage(), e);
			throw new ApiException("400", "Foto tidak dapat dibaca");
		}
	}

	private byte[] encode(BufferedImage source) throws IOException {
		int width = source.getWidth();
		int height = source.getHeight();
		double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(width, height));
		int targetWidth = Math.max(1, (int) Math.round(width * scale));
		int targetHeight = Math.max(1, (int) Math.round(height * scale));
		// JPEG tidak punya transparansi: latar putih untuk PNG transparan.
		BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = output.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, targetWidth, targetHeight);
			g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		} finally {
			g.dispose();
		}
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
				writer.setOutput(out);
				writer.write(null, new IIOImage(output, null, null), param);
			}
			return bytes.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	public void save(String productId, byte[] jpeg) {
		try {
			Path directory = directory();
			Files.createDirectories(directory);
			// Ditulis ke berkas sementara lalu dipindah, supaya pembaca tidak pernah melihat berkas setengah jadi.
			Path temp = Files.createTempFile(directory, "upload-", ".tmp");
			Files.write(temp, jpeg);
			Files.move(temp, file(productId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Log.error(e.getMessage(), e);
			throw new ApiException("500", "Gagal menyimpan foto");
		}
	}

	public byte[] read(String productId) {
		try {
			Path file = file(productId);
			return Files.exists(file) ? Files.readAllBytes(file) : null;
		} catch (IOException e) {
			Log.error(e.getMessage(), e);
			return null;
		}
	}

	public void delete(String productId) {
		try {
			Files.deleteIfExists(file(productId));
		} catch (IOException e) {
			Log.error(e.getMessage(), e);
		}
	}

	private Path directory() {
		return Paths.get(uploadDir, "catalog");
	}

	// productId selalu id sistem (hex); dicek lagi supaya tidak mungkin keluar dari folder.
	private Path file(String productId) {
		if (productId == null || !productId.matches("[A-Za-z0-9_-]{1,64}")) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		return directory().resolve(productId + ".jpg");
	}

}
