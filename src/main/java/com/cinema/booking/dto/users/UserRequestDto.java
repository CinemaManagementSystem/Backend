package com.cinema.booking.dto.users;

import com.cinema.booking.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDto {

    public interface OnCreate {}

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(min = 6, message = "Password must be at least 6 characters")
    @NotBlank(groups = OnCreate.class, message = "Password is required")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    @NotBlank(message = "Status is required")
    private String status;
}
