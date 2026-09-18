package com.jtspringproject.JtSpringProject.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jtspringproject.JtSpringProject.support.PostgresTestBase;
import com.jtspringproject.JtSpringProject.support.WithMockAppUser;

/**
 * The authorization matrix for the REST API.
 *
 * <p>This is the test that would have caught the original defect: every
 * {@code /api/**} path was {@code permitAll()}, so an anonymous caller could
 * read the user table, change any password, and mutate the catalogue.
 *
 * <p>Runs the real filter chain - no {@code addFilters = false} - because the
 * behaviour under test lives in the filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiAuthorizationTest extends PostgresTestBase {

	@Autowired
	private MockMvc mockMvc;

	// ---------------------------------------------------------------------
	// Anonymous callers
	// ---------------------------------------------------------------------

	@Test
	void anonymous_cannotListUsers() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotReadAUser() throws Exception {
		mockMvc.perform(get("/api/users/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotChangeAPassword() throws Exception {
		mockMvc.perform(put("/api/users/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"email\":\"a@b.com\",\"password\":\"newpassword123\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotCreateProducts() throws Exception {
		mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"x\",\"price\":\"1.00\",\"categoryId\":1}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotDeleteProducts() throws Exception {
		mockMvc.perform(delete("/api/products/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotCreateOrders() throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotRefundPayments() throws Exception {
		mockMvc.perform(post("/api/payments/refund/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotTriggerReindex() throws Exception {
		mockMvc.perform(post("/api/search/reindex"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotUseTheChatAssistant() throws Exception {
		mockMvc.perform(post("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"hello\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotReadTheCart() throws Exception {
		mockMvc.perform(get("/api/cart"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotImportTheCatalogue() throws Exception {
		mockMvc.perform(post("/api/admin/catalogue/import")
				.contentType("text/csv")
				.content("external_id,name,price,category_name\n"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotReadAddresses() throws Exception {
		mockMvc.perform(get("/api/addresses"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymous_cannotUseTheGeocoder() throws Exception {
		mockMvc.perform(get("/api/geo/pincode/560001"))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * The landing page, department menu and typeahead are public, and run real
	 * SQL here - the suggestion view and department tree from V14 included.
	 */
	@Test
	void anonymous_mayLoadTheLandingPageDepartmentsAndSuggestions() throws Exception {
		mockMvc.perform(get("/api/storefront/home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		mockMvc.perform(get("/api/categories/tree"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.name == 'Grocery')].children").exists());
		mockMvc.perform(get("/api/products/suggest").param("q", "app"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.text == 'apples')]").exists());
	}

	/** The catalogue is intentionally public. */
	@Test
	void anonymous_mayBrowseProducts() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void anonymous_mayReachLogin() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"nobody\",\"password\":\"wrongpassword\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid credentials."));
	}

	// ---------------------------------------------------------------------
	// Ordinary authenticated users
	// ---------------------------------------------------------------------

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotImportTheCatalogue() throws Exception {
		mockMvc.perform(post("/api/admin/catalogue/import")
				.contentType("text/csv")
				.content("external_id,name,price,category_name\n"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotCheckOutToAnotherCustomersAddress() throws Exception {
		mockMvc.perform(post("/api/cart/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":999999}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotListUsers() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotCreateProducts() throws Exception {
		mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"x\",\"price\":\"1.00\",\"categoryId\":1,\"quantity\":1,\"weight\":1}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotDeleteProducts() throws Exception {
		mockMvc.perform(delete("/api/products/1"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotRefundPayments() throws Exception {
		mockMvc.perform(post("/api/payments/refund/1"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotTriggerReindex() throws Exception {
		mockMvc.perform(post("/api/search/reindex"))
				.andExpect(status().isForbidden());
	}

	/** Regression: reading another account's profile was unrestricted. */
	@Test
	@WithMockAppUser(id = 2)
	void user_cannotReadAnotherUsersProfile() throws Exception {
		mockMvc.perform(get("/api/users/1"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotEditAnotherUsersProfile() throws Exception {
		mockMvc.perform(put("/api/users/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"email\":\"a@b.com\",\"password\":\"newpassword123\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockAppUser(id = 2)
	void user_cannotListAnotherUsersOrders() throws Exception {
		mockMvc.perform(get("/api/orders/user/1"))
				.andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------------------
	// Administrators
	// ---------------------------------------------------------------------

	@Test
	@WithMockAppUser(id = 1, admin = true)
	void admin_mayListUsers() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	/**
	 * The user directory must never carry password hashes, whoever is asking.
	 */
	@Test
	@WithMockAppUser(id = 1, admin = true)
	void admin_userListCarriesNoPasswordHashes() throws Exception {
		mockMvc.perform(get("/api/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].username").exists())
				.andExpect(jsonPath("$.data[*].password").doesNotExist());
	}
}
