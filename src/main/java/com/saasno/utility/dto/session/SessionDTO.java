package com.saasno.utility.dto.session;

import java.util.Date;

// Satu sesi login. handle = hash dari id sesi (id aslinya adalah token dan tidak pernah dikirim); current = sesi pemanggil.
public record SessionDTO(String handle, String userId, String username, String branchName, String ip, String userAgent, Date registered,
		Date expired, boolean current) {
}
