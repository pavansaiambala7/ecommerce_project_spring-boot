package com.jtspringproject.JtSpringProject.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.AddressDao;
import com.jtspringproject.JtSpringProject.dto.request.AddressRequest;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Address;

/**
 * A customer's delivery addresses. Every operation is scoped to the caller:
 * there is no method that takes an address id without also taking its owner.
 */
@Service
public class AddressService {

	/** Generous for a real household, and a cap on what one account can store. */
	static final int MAX_ADDRESSES = 20;

	private final AddressDao addressDao;
	private final userService userService;

	public AddressService(AddressDao addressDao, userService userService) {
		this.addressDao = addressDao;
		this.userService = userService;
	}

	@Transactional(readOnly = true)
	public List<Address> list(int customerId) {
		return addressDao.findByCustomerIdOrderByDefaultAddressDescCreatedAtDesc(customerId);
	}

	@Transactional(readOnly = true)
	public Address requireOwned(int customerId, int addressId) {
		return addressDao.findByIdAndCustomerId(addressId, customerId)
				.orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));
	}

	@Transactional
	public Address create(int customerId, AddressRequest request) {
		long existing = addressDao.countByCustomerId(customerId);
		if (existing >= MAX_ADDRESSES) {
			throw new BusinessRuleException("You can save up to " + MAX_ADDRESSES
					+ " addresses. Remove one you no longer use first.");
		}

		// A first address is the default whether or not the shopper ticked the
		// box, otherwise checkout would have nothing preselected.
		boolean makeDefault = request.isMakeDefault() || existing == 0;
		if (makeDefault) {
			addressDao.clearDefault(customerId);
		}

		Address address = new Address();
		address.setCustomer(userService.requireUserById(customerId));
		apply(address, request);
		address.setDefaultAddress(makeDefault);
		return addressDao.save(address);
	}

	@Transactional
	public Address update(int customerId, int addressId, AddressRequest request) {
		requireOwned(customerId, addressId);
		if (request.isMakeDefault()) {
			addressDao.clearDefault(customerId);
		}
		// Re-read after the bulk update, which clears the persistence context.
		Address address = requireOwned(customerId, addressId);
		apply(address, request);
		if (request.isMakeDefault()) {
			address.setDefaultAddress(true);
		}
		return addressDao.save(address);
	}

	@Transactional
	public Address makeDefault(int customerId, int addressId) {
		requireOwned(customerId, addressId);
		addressDao.clearDefault(customerId);
		Address address = requireOwned(customerId, addressId);
		address.setDefaultAddress(true);
		return addressDao.save(address);
	}

	@Transactional
	public void delete(int customerId, int addressId) {
		Address address = requireOwned(customerId, addressId);
		boolean wasDefault = address.isDefaultAddress();
		addressDao.delete(address);
		addressDao.flush();

		// Removing the default should not leave the shopper with saved
		// addresses and none selected at checkout.
		if (wasDefault) {
			List<Address> remaining = list(customerId);
			if (!remaining.isEmpty()) {
				Address next = remaining.get(0);
				next.setDefaultAddress(true);
				addressDao.save(next);
			}
		}
	}

	private static void apply(Address address, AddressRequest request) {
		address.setFullName(clean(request.getFullName()));
		address.setPhone(clean(request.getPhone()));
		address.setLine1(clean(request.getLine1()));
		address.setLine2(blankToNull(request.getLine2()));
		address.setLandmark(blankToNull(request.getLandmark()));
		address.setCity(clean(request.getCity()));
		address.setState(clean(request.getState()));
		address.setPincode(clean(request.getPincode()));
		address.setLatitude(request.getLatitude());
		address.setLongitude(request.getLongitude());
	}

	private static String clean(String value) {
		return value == null ? null : value.strip().replaceAll("\\s+", " ");
	}

	private static String blankToNull(String value) {
		String cleaned = clean(value);
		return cleaned == null || cleaned.isEmpty() ? null : cleaned;
	}
}
