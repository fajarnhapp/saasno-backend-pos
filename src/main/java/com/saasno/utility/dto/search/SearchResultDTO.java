package com.saasno.utility.dto.search;

// Satu hasil pencarian global. link adalah path halaman di frontend; query (bila ada) mengisi filter halaman itu.
public class SearchResultDTO {

	private final String type;
	private final String title;
	private final String subtitle;
	private final String link;

	public SearchResultDTO(String type, String title, String subtitle, String link) {
		this.type = type;
		this.title = title;
		this.subtitle = subtitle;
		this.link = link;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public String getLink() {
		return link;
	}

}
