package com.giftmarket.address.service;

import com.giftmarket.address.dto.request.AddressRequest;
import com.giftmarket.address.dto.response.AddressResponse;
import com.giftmarket.address.entity.Address;
import com.giftmarket.address.exception.AddressException;
import com.giftmarket.address.repository.AddressRepository;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private static final int MAX_ADDRESS_COUNT = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public List<AddressResponse> getMyAddresses(
            Long userId
    ) {
        validateAuthentication(userId);

        return addressRepository
                .findAllByUserIdOrderByDefaultAddressDescIdDesc(
                        userId
                )
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            Long userId,
            AddressRequest request
    ) {
        User user =
                getUserForUpdate(userId);

        long addressCount =
                addressRepository.countByUserId(
                        userId
                );

        if (addressCount >= MAX_ADDRESS_COUNT) {
            throw new AddressException(
                    "배송지는 최대 10개까지 등록할 수 있습니다."
            );
        }

        boolean firstAddress =
                addressCount == 0;

        boolean makeDefault =
                firstAddress ||
                        request.isDefault();

        if (makeDefault) {
            unsetCurrentDefault(userId);
        }

        Address address =
                Address.create(
                        user,
                        normalizeRequired(
                                request.name()
                        ),
                        normalizeRequired(
                                request.recipientName()
                        ),
                        normalizeRequired(
                                request.phoneNumber()
                        ),
                        normalizeRequired(
                                request.postalCode()
                        ),
                        normalizeRequired(
                                request.address()
                        ),
                        normalizeNullable(
                                request.detailAddress()
                        ),
                        makeDefault
                );

        Address savedAddress =
                addressRepository.save(
                        address
                );

        return AddressResponse.from(
                savedAddress
        );
    }

    @Transactional
    public AddressResponse updateAddress(
            Long userId,
            Long addressId,
            AddressRequest request
    ) {
        getUserForUpdate(userId);

        Address address =
                getMyAddress(
                        userId,
                        addressId
                );

        address.update(
                normalizeRequired(
                        request.name()
                ),
                normalizeRequired(
                        request.recipientName()
                ),
                normalizeRequired(
                        request.phoneNumber()
                ),
                normalizeRequired(
                        request.postalCode()
                ),
                normalizeRequired(
                        request.address()
                ),
                normalizeNullable(
                        request.detailAddress()
                )
        );

        /*
         * 기본배송지 요청이 들어온 경우만 기본배송지를 변경합니다.
         *
         * 기존 기본배송지를 수정하면서 isDefault=false를 보내더라도
         * 기본배송지 자체가 사라지지는 않도록 유지합니다.
         */
        if (
                request.isDefault() &&
                        !address.isDefaultAddress()
        ) {
            unsetCurrentDefault(userId);
            address.setAsDefault();
        }

        return AddressResponse.from(
                address
        );
    }

    @Transactional
    public AddressResponse setDefaultAddress(
            Long userId,
            Long addressId
    ) {
        getUserForUpdate(userId);

        Address address =
                getMyAddress(
                        userId,
                        addressId
                );

        if (address.isDefaultAddress()) {
            return AddressResponse.from(
                    address
            );
        }

        unsetCurrentDefault(userId);

        address.setAsDefault();

        return AddressResponse.from(
                address
        );
    }

    @Transactional
    public void deleteAddress(
            Long userId,
            Long addressId
    ) {
        getUserForUpdate(userId);

        Address address =
                getMyAddress(
                        userId,
                        addressId
                );

        boolean wasDefault =
                address.isDefaultAddress();

        addressRepository.delete(address);

        /*
         * delete SQL을 먼저 DB에 반영한 뒤
         * 남은 배송지에서 새 기본 배송지를 결정합니다.
         */
        addressRepository.flush();

        if (!wasDefault) {
            return;
        }

        List<Address> remainingAddresses =
                addressRepository
                        .findAllByUserIdOrderByDefaultAddressDescIdDesc(
                                userId
                        );

        if (!remainingAddresses.isEmpty()) {
            remainingAddresses
                    .get(0)
                    .setAsDefault();
        }
    }

    private void unsetCurrentDefault(
            Long userId
    ) {
        addressRepository
                .findByUserIdAndDefaultAddressTrue(
                        userId
                )
                .ifPresent(
                        Address::unsetDefault
                );
    }

    private Address getMyAddress(
            Long userId,
            Long addressId
    ) {
        return addressRepository
                .findByIdAndUserId(
                        addressId,
                        userId
                )
                .orElseThrow(() ->
                        new AddressException(
                                "배송지 정보를 찾을 수 없습니다."
                        )
                );
    }

    private User getUserForUpdate(
            Long userId
    ) {
        validateAuthentication(userId);

        return userRepository
                .findByIdForUpdate(
                        userId
                )
                .orElseThrow(() ->
                        new AuthenticationException(
                                "사용자 정보를 찾을 수 없습니다."
                        )
                );
    }

    private void validateAuthentication(
            Long userId
    ) {
        if (userId == null) {
            throw new AuthenticationException(
                    "인증이 필요합니다."
            );
        }
    }

    private String normalizeRequired(
            String value
    ) {
        return value.trim();
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}