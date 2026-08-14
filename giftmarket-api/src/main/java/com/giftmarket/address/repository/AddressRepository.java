package com.giftmarket.address.repository;

import com.giftmarket.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository
        extends JpaRepository<Address, Long> {

    List<Address> findAllByUserIdOrderByDefaultAddressDescIdDesc(
            Long userId
    );

    Optional<Address> findByIdAndUserId(
            Long addressId,
            Long userId
    );

    Optional<Address> findByUserIdAndDefaultAddressTrue(
            Long userId
    );

    boolean existsByUserId(
            Long userId
    );

    long countByUserId(
            Long userId
    );
}