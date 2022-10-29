package com.concordeu.auth.validation;

import com.concordeu.auth.dto.AuthUserRequestDto;
import com.concordeu.auth.excaption.InvalidRequestDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("unit")
class ValidateRequestImplTest {

    ValidateRequest testValidateRequest;

    @BeforeEach
    void setUp() {
        testValidateRequest = new ValidateRequestImpl();
    }

    @Test
    void validateRequestShouldReturnTrue() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsWhitespace() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888 888 8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsDots() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888.888.8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsHyphens() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888-888-8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfFirstPartOfPhoneIsBetweenParentheses() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "(888) 888 8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueWithInternationalPrefixAtTheStarToFPhoneIsBetweenParentheses() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "+111 (202) 555-0125",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateRequest.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberLessThanTenDigits() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberMoreThanTenDigits() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "88888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberContainNotAllowOptional() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888/888/8888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailUsernameMissing() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "@gmail.com",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Email is not correct: %s. For example: example@gmail.com", requestDto.email()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingElement() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivangmail.com",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Email is not correct: %s. For example: example@gmail.com", requestDto.email()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingPrefix() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@.com",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Email is not correct: %s. For example: example@gmail.com", requestDto.email()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingDot() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmailcom",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Email is not correct: %s. For example: example@gmail.com", requestDto.email()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingCom() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivangmail.",
                "888 888 8888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Email is not correct: %s. For example: example@gmail.com", requestDto.email()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfPostCodeMissing() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888 888 8888",
                "Varna",
                "Katay",
                "");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Post code is not correct: %s. For example: 9001", requestDto.postCode()));

    }

    @Test
    void validateRequestShouldThrowExceptionIfStreetMissing() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888 888 8888",
                "Varna",
                "",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Street is not correct: %s. For example: Katya Paskaleva", requestDto.street()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfCityMissing() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "888 888 8888",
                "",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("City is not correct: %s. For example: Varna", requestDto.city()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfLastNameDoesNotContainUppercaseLetter() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Last name is not correct: %s. For example: Ivanov", requestDto.lastName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfLastNameContainsDigit() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Ivan",
                "Iva2nov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Last name is not correct: %s. For example: Ivanov", requestDto.lastName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfFirstNameDoesNotContainUppercaseLetter() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("First name is not correct: %s. For example: Ivan", requestDto.firstName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfFirstNameContainsDigit() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123!@#",
                "Iva2n",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("First name is not correct: %s. For example: Ivan", requestDto.firstName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneDigit() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abcasd!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct. For example: Abc123!@#");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneLowerCaseLetter() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "ACB123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct. For example: Abc123!@#");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneUpperCaseLetter() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct. For example: Abc123!@#");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneSpecialCharacter() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123asd",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct. For example: Abc123!@#");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainWhitespaces() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                "ivanIvanov",
                "Abc123 !@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct. For example: Abc123!@#");
    }

    @Test
    void validateRequestShouldThrowExceptionIfUsernameMissing() {
        AuthUserRequestDto requestDto = new AuthUserRequestDto(
                " ",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "ivan@gmail.com",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateRequest.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(String.format("Username is not correct: %s. For example: ivanIvanov", requestDto.username()));
    }



}