package com.jtspringproject.JtSpringProject.dto.response;

import com.jtspringproject.JtSpringProject.models.User;

/**
 * Public view of a user. Carries no password field of any kind: the previous
 * endpoints returned the entity, publishing every account's BCrypt hash.
 */
public class UserResponse {

    private int id;
    private String username;
    private String email;
    private String address;
    private String role;

    public UserResponse() {
    }

    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        UserResponse dto = new UserResponse();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.address = user.getAddress();
        dto.role = user.getRole();
        return dto;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
