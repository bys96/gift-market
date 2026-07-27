import { API_BASE_URL } from "@/lib/api";

export default function LoginPage() {
  const googleLoginUrl = `${API_BASE_URL}/oauth2/authorization/google`;
  const kakaoLoginUrl = `${API_BASE_URL}/oauth2/authorization/kakao`;

  return (
    <main>
      <h1>로그인</h1>

      <a href={googleLoginUrl}>Google로 로그인</a>
      <br />
      <a href={kakaoLoginUrl}>카카오로 로그인</a>
    </main>
  );
}
