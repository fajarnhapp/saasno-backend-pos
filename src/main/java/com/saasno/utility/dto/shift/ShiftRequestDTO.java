package com.saasno.utility.dto.shift;

import java.math.BigDecimal;

// Isian buka/tutup shift. branchId hanya dipakai user global saat membuka; id hanya untuk tutup paksa.
public record ShiftRequestDTO(String id, String branchId, BigDecimal openingCash, BigDecimal countedCash, String note) {
}
