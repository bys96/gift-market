package com.giftmarket.address.dto.response;

import com.giftmarket.address.entity.Address;

public record AddressResponse(

        Long id,

        String name,

        String recipientName,

        String phoneNumber,

        String postalCode,

        String address,

        String detailAddress,

        boolean isDefault

) {

    public static AddressResponse from(
            Address address
    ) {
        return new AddressResponse(
                address.getId(),
                address.getName(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getPostalCode(),
                address.getAddress(),
                address.getDetailAddress(),
                address.isDefaultAddress()
        );
    }
}