package com.concordeu.profile.validation;

import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("unit")
class ValidateDataImplTest {

    ValidateData testValidateData;

    @BeforeEach
    void setUp() {
        testValidateData = new ValidateDataImpl();
    }

    @Test
    void validateRequestShouldReturnTrue() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsWhitespace() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888 888 8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsDots() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888.888.8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfPhoneNumberContainsHyphens() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888-888-8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueIfFirstPartOfPhoneIsBetweenParentheses() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "(888) 888 8880",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldReturnTrueWithInternationalPrefixAtTheStarToFPhoneIsBetweenParentheses() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "+111 (202) 555-0125",
                "Varna",
                "Katay",
                "9999");

        assertThat(testValidateData.validateRequest(requestDto)).isTrue();
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberLessThanTenDigits() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberMoreThanTenDigits() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "88888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenPhoneNumberContainNotAllowOptional() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888/888/8888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Phone number is not correct: %s. For example: 2055550125", requestDto.phoneNumber()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailUsernameMissing() {
        UserRequestDto requestDto = new UserRequestDto(
                "@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingElement() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivangmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingPrefix() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingDot() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmailcom",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }

    @Test
    void validateRequestShouldThrowExceptionWhenEmailDomainMissingCom() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivangmail.",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888 888 8888",
                "Varna",
                "Katay",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfPostCodeMissing() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888 888 8888",
                "Varna",
                "Katay",
                "");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Post code is not correct: %s. For example: 9001", requestDto.postCode()));

    }

    @Test
    void validateRequestShouldThrowExceptionIfStreetMissing() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888 888 8888",
                "Varna",
                "",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Street is not correct: %s. For example: Katya Paskaleva", requestDto.street()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfCityMissing() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "888 888 8888",
                "",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("City is not correct: %s. For example: Varna", requestDto.city()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfLastNameDoesNotContainUppercaseLetter() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Last name is not correct: %s. For example: Ivanov", requestDto.lastName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfLastNameContainsDigit() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Iva2nov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("Last name is not correct: %s. For example: Ivanov", requestDto.lastName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfFirstNameDoesNotContainUppercaseLetter() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("First name is not correct: %s. For example: Ivan", requestDto.firstName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfFirstNameContainsDigit() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123!@#",
                "Iva2n",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(
                        String.format("First name is not correct: %s. For example: Ivan", requestDto.firstName()));
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneDigit() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abcasd!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneLowerCaseLetter() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "ACB123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneUpperCaseLetter() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainAtLeastOneSpecialCharacter() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123asd",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct");
    }

    @Test
    void validateRequestShouldThrowExceptionIfPasswordDoesNotContainWhitespaces() {
        UserRequestDto requestDto = new UserRequestDto(
                "ivan@gmail.com",
                "Abc123 !@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Password is not correct");
    }

    @Test
    void validateRequestShouldThrowExceptionIfUsernameMissing() {
        UserRequestDto requestDto = new UserRequestDto(
                " ",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "8888888888",
                "Varna",
                "Katya",
                "9999");
        assertThatThrownBy(() -> testValidateData.validateRequest(requestDto))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining(String.format("Username is not correct: %s. For example: example@gmail.com", requestDto.username()));
    }



}