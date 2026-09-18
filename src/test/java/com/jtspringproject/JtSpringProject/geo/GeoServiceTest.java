package com.jtspringproject.JtSpringProject.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtspringproject.JtSpringProject.exception.ServiceUnavailableException;

/** Parsing is tested against responses captured from the live services on 2026-09-17. */
class GeoServiceTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void parseReverse_shouldMapANominatimAddressToAnIndianAddressForm() throws Exception {
		String response = """
				{"display_name":"Hill Ridge Appartments, ISB Road, Gachibowli, Hyderabad, Telangana, 500032, India",
				 "address":{"road":"ISB Road","residential":"Hill Ridge Appartments",
				  "suburb":"Ward 106 Serilingampally",
				  "city_district":"Greater Hyderabad Municipal Corporation West Zone",
				  "city":"Hyderabad","county":"Serilingampalle mandal","state_district":"Ranga Reddy",
				  "state":"Telangana","ISO3166-2-lvl4":"IN-TS","postcode":"500032",
				  "country":"India","country_code":"in"}}
				""";

		GeoService.GeoAddress address = GeoService.parseReverse(json.readTree(response));

		assertEquals("Hyderabad", address.city());
		assertEquals("Telangana", address.state());
		assertEquals("500032", address.pincode());
		// The administrative ward is dropped; nobody writes it on a parcel.
		assertEquals("ISB Road, Hill Ridge Appartments", address.line2());
	}

	@Test
	void parseReverse_shouldFallBackToTownOrVillageWhenThereIsNoCity() throws Exception {
		String response = """
				{"address":{"village":"Kumily","state":"Kerala","postcode":"685 509"}}
				""";

		GeoService.GeoAddress address = GeoService.parseReverse(json.readTree(response));

		assertEquals("Kumily", address.city());
		assertEquals("685509", address.pincode());
	}

	@Test
	void parseReverse_shouldAskForManualEntryWhenNothingIsFound() throws Exception {
		assertThrows(ServiceUnavailableException.class,
				() -> GeoService.parseReverse(json.readTree("{\"error\":\"Unable to geocode\"}")));
	}

	@Test
	void parsePincode_shouldReturnDistrictStateAndPostOffices() throws Exception {
		String response = """
				[{"Message":"Number of pincode(s) found:2","Status":"Success","PostOffice":[
				  {"Name":"Gachibowli","District":"K.V.Rangareddy","State":"Telangana"},
				  {"Name":"Manuu","District":"Hyderabad","State":"Telangana"}]}]
				""";

		GeoService.PincodeInfo info = GeoService.parsePincode("500032", json.readTree(response)).orElseThrow();

		assertEquals("K.V.Rangareddy", info.city());
		assertEquals("Telangana", info.state());
		assertEquals(List.of("Gachibowli", "Manuu"), info.areas());
	}

	@Test
	void parsePincode_shouldBeEmptyForAnUnknownPincode() throws Exception {
		String response = "[{\"Message\":\"No records found\",\"Status\":\"Error\",\"PostOffice\":null}]";

		assertTrue(GeoService.parsePincode("999999", json.readTree(response)).isEmpty());
	}

	@Test
	void pincode_shouldRejectMalformedInputWithoutCallingOut() {
		GeoService service = new GeoService(json, "http://unused", "http://unused/", "test");

		assertThrows(IllegalArgumentException.class, () -> service.pincode("012345"));
		assertThrows(IllegalArgumentException.class, () -> service.pincode("56001"));
	}
}
