package com.jtspringproject.JtSpringProject.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jtspringproject.JtSpringProject.models.Address;

@Repository
public interface AddressDao extends JpaRepository<Address, Integer> {

	/** Default first, then most recently added - the order checkout offers them in. */
	List<Address> findByCustomerIdOrderByDefaultAddressDescCreatedAtDesc(int customerId);

	/**
	 * Looks an address up only within its owner's book. A caller naming someone
	 * else's address id gets "not found", which reveals nothing about whether
	 * that id exists.
	 */
	Optional<Address> findByIdAndCustomerId(int id, int customerId);

	long countByCustomerId(int customerId);

	/**
	 * Clears the current default before a new one is set. Runs as its own
	 * statement first because the partial unique index allows only one default
	 * per customer at any instant, including mid-transaction.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Address a SET a.defaultAddress = false WHERE a.customer.id = :customerId AND a.defaultAddress = true")
	int clearDefault(@Param("customerId") int customerId);
}
