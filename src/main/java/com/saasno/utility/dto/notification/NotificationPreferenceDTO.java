package com.saasno.utility.dto.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Permintaan hanya memakai category dan enabled; label dan description diisi sistem.
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPreferenceDTO {

	private String category;
	private String label;
	private String description;
	private Boolean enabled;

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

}
