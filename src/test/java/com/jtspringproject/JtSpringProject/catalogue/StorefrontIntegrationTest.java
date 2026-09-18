package com.jtspringproject.JtSpringProject.catalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.jtspringproject.JtSpringProject.ai.service.CatalogueSearchService;
import com.jtspringproject.JtSpringProject.dto.request.AddressRequest;
import com.jtspringproject.JtSpringProject.dto.request.CatalogueQuery;
import com.jtspringproject.JtSpringProject.dto.response.CategoryTreeResponse;
import com.jtspringproject.JtSpringProject.dto.response.ProductResponse;
import com.jtspringproject.JtSpringProject.dto.response.StorefrontHomeResponse;
import com.jtspringproject.JtSpringProject.dto.response.SuggestionResponse;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Address;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.services.AddressService;
import com.jtspringproject.JtSpringProject.services.cartService;
import com.jtspringproject.JtSpringProject.support.PostgresTestBase;

/**
 * The storefront features that live mostly in SQL - the V14 schema, COPY
 * import, the suggestion view, department subtrees, discount ordering and the
 * order address snapshot - exercised against real PostgreSQL with pgvector.
 */
@SpringBootTest
@ActiveProfiles("test")
class StorefrontIntegrationTest extends PostgresTestBase {

	private static final String HEADER = "external_id,name,description,image,price,mrp,quantity,weight,brand,"
			+ "rating,rating_count,category_name\n";

	@Autowired
	private CatalogueImportService importService;
	@Autowired
	private SuggestionService suggestionService;
	@Autowired
	private CatalogueSearchService searchService;
	@Autowired
	private StorefrontService storefrontService;
	@Autowired
	private AddressService addressService;
	@Autowired
	private cartService cartService;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE ship_pincode = '560034')");
		jdbc.update("DELETE FROM orders WHERE ship_pincode = '560034'");
		jdbc.update("DELETE FROM cart_product WHERE product_id IN (SELECT product_id FROM product WHERE external_id LIKE 'it-%')");
		jdbc.update("DELETE FROM product WHERE external_id LIKE 'it-%'");
		jdbc.update("DELETE FROM address WHERE pincode = '560034'");
		suggestionService.refresh();
		storefrontService.evict();
	}

	private static ByteArrayInputStream csv(String body) {
		return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
	}

	private static String fashionRows() {
		return HEADER
				+ "it-1,Peter England Slim Fit Shirt (M),Cotton shirt,,1299,2599,10,300,Peter England,4.2,120,Men's Fashion\n"
				+ "it-2,Peter England Formal Trousers (L),Poly blend,,1499,1999,5,400,Peter England,4.0,80,Men's Fashion\n"
				+ "it-3,Biba Anarkali Kurta (S),Rayon kurta,,899,899,8,250,Biba,4.4,300,Women's Fashion\n"
				+ "it-4,Zzqvortex Unknown Thing,Nowhere,,10,,1,1,Nobody,4.0,1,No Such Department\n";
	}

	@Test
	void import_shouldLoadRowsSkipUnknownDepartmentsAndUpdateOnReimport() throws Exception {
		CatalogueImportService.ImportResult first = importService.importCsv(csv(fashionRows()));

		assertEquals(4, first.rowsRead());
		assertEquals(3, first.inserted());
		assertEquals(0, first.updated());
		assertEquals(1, first.skippedUnknownDepartment());

		// Pretend the first import was embedded, then re-import with one name changed.
		jdbc.update("UPDATE product SET embedding = array_fill(0.01, ARRAY[768])::vector WHERE external_id LIKE 'it-%'");
		String changed = fashionRows().replace("Peter England Slim Fit Shirt (M)", "Peter England Oxford Shirt (M)");
		CatalogueImportService.ImportResult second = importService.importCsv(csv(changed));

		assertEquals(0, second.inserted());
		assertEquals(3, second.updated());
		// Only the renamed product needs a new vector.
		assertNull(jdbc.queryForObject("SELECT embedding::text FROM product WHERE external_id = 'it-1'", String.class));
		assertNotNull(jdbc.queryForObject("SELECT embedding::text FROM product WHERE external_id = 'it-2'", String.class));
	}

	@Test
	void import_shouldAcceptGzipAndFilesWithoutTheNewerColumns() throws Exception {
		String legacy = "external_id,name,price,quantity,category_name\n"
				+ "it-10,Legacy Format Kettle,799,3,Home & Kitchen\n";
		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
			out.write(legacy.getBytes(StandardCharsets.UTF_8));
		}

		CatalogueImportService.ImportResult result = importService.importCsv(new ByteArrayInputStream(gz.toByteArray()));

		assertEquals(1, result.inserted());
	}

	@Test
	void import_shouldRejectAnUnknownColumnBeforeWritingAnything() {
		BusinessRuleException error = assertThrows(BusinessRuleException.class,
				() -> importService.importCsv(csv("external_id,name,price,category_name,password\n")));
		assertTrue(error.getMessage().contains("password"));
	}

	@Test
	void import_shouldRollBackEntirelyOnAMalformedRow() {
		String broken = HEADER + "it-20,Good Row,,,100,,1,1,Brand,4.0,1,Books\n"
				+ "it-21,Bad Row,,,not-a-price,,1,1,Brand,4.0,1,Books\n";

		assertThrows(BusinessRuleException.class, () -> importService.importCsv(csv(broken)));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM product WHERE external_id = 'it-20'", Integer.class));
	}

	@Test
	void suggestions_shouldCompletePrefixesPreferDepartmentsAndTolerateTypos() throws Exception {
		importService.importCsv(csv(fashionRows()));

		List<String> peter = suggestionService.suggest("pet", 8).stream().map(SuggestionResponse::text).toList();
		assertTrue(peter.contains("peter england"), peter.toString());

		SuggestionResponse department = suggestionService.suggest("men", 8).get(0);
		assertEquals("men's fashion", department.text());
		assertNotNull(department.categoryId());

		List<String> typo = suggestionService.suggest("anarkaly", 8).stream().map(SuggestionResponse::text).toList();
		assertTrue(typo.contains("anarkali"), typo.toString());

		// LIKE wildcards in input match literally rather than matching everything.
		assertTrue(suggestionService.suggest("%", 8).isEmpty());
	}

	@Test
	void search_shouldIncludeChildDepartmentsAndSortByDiscount() throws Exception {
		importService.importCsv(csv(fashionRows()));
		Integer fashion = jdbc.queryForObject("SELECT category_id FROM category WHERE name = 'Fashion'", Integer.class);

		CatalogueQuery query = new CatalogueQuery();
		query.setCategoryId(fashion);
		query.setSort("discount");
		List<ProductResponse> items = searchService.search(query).items();

		assertEquals(List.of("it-1 50", "it-2 25", "it-3 0"), items.stream()
				.filter(p -> p.getName().startsWith("Peter") || p.getName().startsWith("Biba"))
				.map(p -> (p.getName().contains("Shirt") ? "it-1" : p.getName().contains("Trousers") ? "it-2" : "it-3")
						+ " " + p.getDiscountPercent())
				.toList());
	}

	/**
	 * Full-text search requires every word, so a shopper typing a sentence used
	 * to get an empty page. Without embeddings - a fresh catalogue, or an
	 * unreachable embedding service - that was every such search.
	 */
	@Test
	void search_shouldFallBackToAnyWordWhenNothingMatchesThemAll() throws Exception {
		importService.importCsv(csv(fashionRows()));

		CatalogueQuery everyWord = new CatalogueQuery();
		everyWord.setQ("cotton shirt for the office");
		List<ProductResponse> items = searchService.search(everyWord).items();

		assertTrue(items.stream().anyMatch(p -> p.getName().contains("Shirt")), items.toString());

		// A search that does match every word still ranks by that stricter query.
		CatalogueQuery precise = new CatalogueQuery();
		precise.setQ("Peter England Slim Fit Shirt");
		assertTrue(searchService.search(precise).items().get(0).getName().contains("Slim Fit Shirt"));
	}

	@Test
	void categoryTree_shouldNestDepartmentsAndCountChildren() throws Exception {
		importService.importCsv(csv(fashionRows()));

		CategoryTreeResponse fashion = searchService.categoryTree().stream()
				.filter(c -> c.name().equals("Fashion")).findFirst().orElseThrow();

		assertTrue(fashion.productCount() >= 3);
		assertTrue(fashion.children().stream().anyMatch(c -> c.name().equals("Men's Fashion")));
	}

	@Test
	void home_shouldBuildCardsWhoseHeadlinesMatchTheData() throws Exception {
		importService.importCsv(csv(fashionRows()));

		StorefrontHomeResponse home = storefrontService.home();

		// Grocery comes from the V8 seed with V14's MRPs, so it always has four
		// in-stock products and real discounts.
		StorefrontHomeResponse.Card grocery = home.cards().stream()
				.filter(c -> c.key().equals("grocery")).findFirst().orElseThrow();
		Integer best = jdbc.queryForObject("""
				SELECT max(discount_percent) FROM product p JOIN category c ON c.category_id = p.category_id
				JOIN category parent ON parent.category_id = c.parent_id
				WHERE parent.name = 'Grocery' AND p.quantity > 0
				""", Integer.class);
		assertEquals("Up to " + best + "% off", grocery.headline());
		assertEquals(4, grocery.products().size());
		assertTrue(home.deals().stream().allMatch(p -> p.getDiscountPercent() >= 10 && p.isInStock()));
	}

	private static AddressRequest address(String name, boolean makeDefault) {
		AddressRequest request = new AddressRequest();
		request.setFullName(name);
		request.setPhone("9876543210");
		request.setLine1("42, 5th Cross");
		request.setLine2("Koramangala");
		request.setCity("Bengaluru");
		request.setState("Karnataka");
		request.setPincode("560034");
		request.setMakeDefault(makeDefault);
		return request;
	}

	@Test
	void addresses_shouldKeepExactlyOneDefaultAndStayPrivate() {
		int owner = jdbc.queryForObject("SELECT id FROM customer WHERE username = 'lisa'", Integer.class);
		int other = jdbc.queryForObject("SELECT id FROM customer WHERE username = 'admin'", Integer.class);

		Address home = addressService.create(owner, address("Home", false));
		assertTrue(home.isDefaultAddress(), "a first address becomes the default");

		Address office = addressService.create(owner, address("Office", true));
		List<Address> book = addressService.list(owner);
		assertEquals(List.of("Office"), book.stream().filter(Address::isDefaultAddress).map(Address::getFullName).toList());

		addressService.delete(owner, office.getId());
		assertTrue(addressService.list(owner).get(0).isDefaultAddress(), "deleting the default promotes another");

		assertThrows(ResourceNotFoundException.class, () -> addressService.requireOwned(other, home.getId()));
	}

	@Test
	void checkout_shouldFreezeTheAddressOntoTheOrder() throws Exception {
		importService.importCsv(csv(fashionRows()));
		int owner = jdbc.queryForObject("SELECT id FROM customer WHERE username = 'lisa'", Integer.class);
		int productId = jdbc.queryForObject("SELECT product_id FROM product WHERE external_id = 'it-3'", Integer.class);

		Address home = addressService.create(owner, address("Asha Rao", false));
		cartService.addItem(owner, productId, 1);
		Order order = cartService.checkout(owner, home.getId());

		// Editing the address book afterwards must not move the parcel.
		AddressRequest moved = address("Asha Rao", false);
		moved.setLine1("Somewhere else entirely");
		addressService.update(owner, home.getId(), moved);

		String shippedTo = jdbc.queryForObject("SELECT ship_line1 FROM orders WHERE id = ?", String.class, order.getId());
		assertEquals("42, 5th Cross", shippedTo);
	}
}
