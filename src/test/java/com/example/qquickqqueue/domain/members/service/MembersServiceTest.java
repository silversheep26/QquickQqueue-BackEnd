package com.example.qquickqqueue.domain.members.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.qquickqqueue.domain.enumPackage.Gender;
import com.example.qquickqqueue.domain.members.dto.request.LoginRequestDto;
import com.example.qquickqqueue.domain.members.dto.request.SignupRequestDto;
import com.example.qquickqqueue.domain.members.entity.Members;
import com.example.qquickqqueue.domain.members.repository.MembersRepository;
import com.example.qquickqqueue.redis.util.RedisUtil;
import com.example.qquickqqueue.security.jwt.JwtUtil;
import com.example.qquickqqueue.security.jwt.TokenDto;
import com.example.qquickqqueue.util.Message;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class})
class MembersServiceTest {
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private MembersRepository membersRepository;
	@Mock
	private HttpServletResponse httpServletResponse;
	@Mock
	private HttpServletRequest httpServletRequest;
	@Mock
	private RedisUtil redisUtil;
	@Mock
	private JwtUtil jwtUtil;
	@InjectMocks
	private MembersService membersService;

	Members member = Members.builder()
		.email("test@test.com")
		.password("test1234")
		.name("tester")
		.gender(Gender.FEMALE)
		.birth(LocalDate.now())
		.phoneNumber("010-123-1234")
		.build();

	@Nested
	@DisplayName("회원가입 테스트")
	class signup {

		@DisplayName("회원가입 성공")
		@Test
		void signup_success() {
			//given
			SignupRequestDto requestDto = SignupRequestDto.builder().email("test@test.com")
				.password("test1234")
				.name("tester")
				.birth(LocalDate.now())
				.gender(Gender.FEMALE)
				.phoneNumber("010-123-1234")
				.build();

			//when
			ResponseEntity<Message> response = membersService.signup(requestDto);

			//then
			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals("회원가입 성공", response.getBody().getMessage());
		}

		@Test
		@DisplayName("이미 가입한 이메일로 회원가입")
		void signup_alreadyExistEmail() {
			// given
			SignupRequestDto duplRequestDto = SignupRequestDto.builder().email("test@test.com")
				.password("test1234")
				.name("tester")
				.gender(Gender.FEMALE)
				.phoneNumber("010-123-1234")
				.build();
			String email = duplRequestDto.getEmail();

			// when
			when(membersRepository.findByEmail(email)).thenReturn(Optional.of(member));

			// then
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> membersService.signup(duplRequestDto));
			assertEquals("이미 존재하는 이메일입니다. email : " + email, exception.getMessage());
		}

		@Test
		@DisplayName("이미 탈퇴한 이메일로 회원가입")
		void signup_already_withdrawal() {
			// given
			SignupRequestDto duplRequestDto = SignupRequestDto.builder().email("test@test.com")
				.password("test1234")
				.name("tester")
				.gender(Gender.FEMALE)
				.phoneNumber("010-123-1234")
				.build();
			String email = duplRequestDto.getEmail();

			Members withdrawalMember = Members.builder()
				.email("test@test.com")
				.password("test1234")
				.name("tester")
				.gender(Gender.FEMALE)
				.birth(LocalDate.now())
				.phoneNumber("010-123-1234")
				.outDate(LocalDate.now())
				.build();

			// when
			when(membersRepository.findByEmail(email)).thenReturn(Optional.of(withdrawalMember));

			// then
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> membersService.signup(duplRequestDto));
			assertEquals("이미 탈퇴한 이메일입니다. email : " + email, exception.getMessage());
		}
	}

	@Nested
	@DisplayName("로그인 테스트")
	class login{

		@Test
		@DisplayName("로그인 성공 테스트")
		void login_success() {
			// given
			LoginRequestDto requestDto = LoginRequestDto.builder()
				.email("test@test.com").password("test1234").build();
			TokenDto tokenDto = new TokenDto("ACCESS-TOKEN", "REFRESH-TOKEN");

			when(membersRepository.findByEmail(requestDto.getEmail())).thenReturn(
				Optional.of(member));
			when(
				passwordEncoder.matches(requestDto.getPassword(), member.getPassword())).thenReturn(
				true);
			when(jwtUtil.createAllToken(member)).thenReturn(tokenDto);

			// when
			ResponseEntity<Message> response = membersService.login(requestDto,httpServletResponse);

			// then
			assertEquals( HttpStatus.OK, response.getStatusCode());
			assertEquals("로그인 성공", response.getBody().getMessage());
		}

		@Test
		@DisplayName("로그인시 비밀번호 틀림")
		void login_password_wrong() {
			LoginRequestDto requestDto = LoginRequestDto.builder()
				.email("test@test.com").password("test123").build();

			when(membersRepository.findByEmail(requestDto.getEmail())).thenReturn(
				Optional.of(member));
			when(
				passwordEncoder.matches(requestDto.getPassword(), member.getPassword())).thenReturn(
				false);

			// when then
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> membersService.login(requestDto, httpServletResponse));
			assertEquals("비밀번호를 틀렸습니다.", exception.getMessage());
		}

		@Test
		@DisplayName("사용자를 찾을 수 없음")
		void login_notFound_User() {
			LoginRequestDto requestDto = LoginRequestDto.builder()
				.email("test1@test.com").password("test1234").build();
			when(membersRepository.findByEmail(requestDto.getEmail())).thenReturn(Optional.empty());

			UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
				() -> membersService.login(requestDto, httpServletResponse));
			assertEquals("사용자를 찾을 수 없습니다. email : " + requestDto.getEmail(),
				exception.getMessage());
		}
	}

	@Nested
	@DisplayName("로그 아웃")
	class logout {

		@Test
		@DisplayName("로그 아웃 성공")
		void logout_success() {
			// given
			String accessToken = "accesstokenIdontknow";
			String refreshToken = "refreshtokenIdontknow";
			String email = member.getEmail();

			when(httpServletRequest.getHeader("ACCESS-TOKEN")).thenReturn("Bearer " + accessToken);
			when(redisUtil.get(email)).thenReturn(refreshToken);
			when(jwtUtil.getExpirationTime(accessToken)).thenReturn(1L);

			// when
			ResponseEntity<Message> response = membersService.logout(member, httpServletRequest);

			// then
			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertEquals("로그아웃 성공", response.getBody().getMessage());
		}

		@Test
		@DisplayName("로그아웃시 리프레시 없음(유저 못찾음)")
		void logout_NotFound_User() {
			//given
			String accessToken = "accessTokenIdontknow";
			String email = member.getEmail();

			when(httpServletRequest.getHeader("ACCESS-TOKEN")).thenReturn("Bearer " + accessToken);
			when(redisUtil.get(email)).thenReturn("");

			// when then
			UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
				() -> membersService.logout(member, httpServletRequest));
			assertEquals("유저를 찾지 못했습니다. email : " + member.getEmail(), exception.getMessage());

		}
	}
}
