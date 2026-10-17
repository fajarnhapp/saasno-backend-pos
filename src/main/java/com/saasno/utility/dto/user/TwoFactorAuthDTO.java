package com.saasno.utility.dto.user;

public class TwoFactorAuthDTO {

	private String setupUri;

	public TwoFactorAuthDTO() {
	}

	public TwoFactorAuthDTO(String setupUri) {
		this.setupUri = setupUri;
	}

	public String getSetupUri() {
		return setupUri;
	}

	public void setSetupUri(String setupUri) {
		this.setupUri = setupUri;
	}

}
