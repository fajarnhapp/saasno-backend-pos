package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.UserService;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.constant.Path;

import com.saasno.utility.dto.user.UserDTO;

import org.springframework.web.bind.annotation.PutMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_USER)
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseModel<UserDTO> create(@Valid @RequestBody UserDTO user) {
        return userService.create(user);
    }

    @GetMapping
    public ResponseEntity<DataResult<UserDTO>> read(HttpServletRequest request) {
        return userService.read(request);
    }

    @PutMapping
    public ResponseModel<UserDTO> update(@RequestBody UserDTO user) {
        return userService.update(user);
    }

    @DeleteMapping
    public ResponseModel<UserDTO> delete(@RequestParam("id") String id) {
        return userService.delete(id);
    }

    @GetMapping("/me")
    public ResponseModel<UserDTO> getMe() {
        return userService.findByUsername();
    }

    @PutMapping("/reset-password")
    public ResponseModel<UserDTO> resetPassword(@RequestParam("username") String username) {
        return userService.resetPassword(username);
    }

    @PutMapping("/change-password")
    public ResponseModel<UserDTO> changePassword(
            @RequestParam("password") String password,
            @RequestParam("oldPassword") String oldPassword) {
        return userService.changePassword(password, oldPassword);
    }

    @PostMapping("/verify-password")
    public ResponseModel<Boolean> verifyPassword(@RequestParam("password") String password) {
        return userService.verifyPassword(password);
    }
}
