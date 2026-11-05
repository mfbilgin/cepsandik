package com.cepsandik.userservice.dtos.responses;

import java.util.List;

/**
 * passed=false ise reasons, hangi kontrolün başarısız olduğunu makine-okunur
 * kodlarla listeler (ör. DEVICE_INTEGRITY_NOT_MET).
 */
public record IntegrityVerdictResponse(boolean passed, List<String> reasons) {
}
