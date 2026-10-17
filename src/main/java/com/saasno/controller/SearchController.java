package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.SearchService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.search.SearchResultDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_SEARCH)
public class SearchController {

	@Autowired
	private SearchService searchService;

	@GetMapping
	public ResponseModel<List<SearchResultDTO>> search(@RequestParam(value = "q", required = false) String query) {
		return searchService.search(query);
	}

}
