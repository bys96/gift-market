export function formatKoreanPhoneNumber(value: string): string {
  const numbers = value.replace(/\D/g, "").slice(0, 11);

  if (!numbers) {
    return "";
  }

  // 서울 지역번호
  if (numbers.startsWith("02")) {
    if (numbers.length <= 2) {
      return numbers;
    }

    if (numbers.length <= 5) {
      return `${numbers.slice(0, 2)}-${numbers.slice(2)}`;
    }

    if (numbers.length <= 9) {
      return `${numbers.slice(0, 2)}-${numbers.slice(
        2,
        5,
      )}-${numbers.slice(5)}`;
    }

    return `${numbers.slice(0, 2)}-${numbers.slice(2, 6)}-${numbers.slice(6)}`;
  }

  // 휴대폰 / 지역번호 / 인터넷전화 등
  if (numbers.length <= 3) {
    return numbers;
  }

  if (numbers.length <= 6) {
    return `${numbers.slice(0, 3)}-${numbers.slice(3)}`;
  }

  if (numbers.length <= 10) {
    return `${numbers.slice(0, 3)}-${numbers.slice(3, 6)}-${numbers.slice(6)}`;
  }

  return `${numbers.slice(0, 3)}-${numbers.slice(3, 7)}-${numbers.slice(7)}`;
}
