package site.devtown.spadeworker.domain.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.devtown.spadeworker.domain.auth.exception.InvalidTokenException;
import site.devtown.spadeworker.domain.auth.model.UserPrincipal;
import site.devtown.spadeworker.domain.auth.repository.UserRefreshTokenRepository;
import site.devtown.spadeworker.domain.auth.token.AuthToken;
import site.devtown.spadeworker.domain.auth.token.AuthTokenProvider;
import site.devtown.spadeworker.domain.auth.token.UserRefreshToken;
import site.devtown.spadeworker.domain.user.model.constant.UserRoleType;
import site.devtown.spadeworker.domain.user.model.entity.User;
import site.devtown.spadeworker.domain.user.repository.UserRepository;

import javax.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static site.devtown.spadeworker.domain.auth.exception.AuthExceptionCode.INVALID_REFRESH_TOKEN;

@RequiredArgsConstructor
@Service
public class JwtService {

    private final UserRepository userRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final AuthTokenProvider tokenProvider;

    /**
     * Access Token 생성
     */
    public AuthToken createAccessToken(
            String personalId,
            List<UserRoleType> roles
    ) {
        return tokenProvider.generateAccessToken(personalId, roles);
    }

    /**
     * Refresh Token 생성
     */
    public AuthToken createRefreshToken(
            String personalId,
            List<UserRoleType> roles
    ) {
        return tokenProvider.generateRefreshToken(personalId, roles);
    }

    /**
     * refresh token 확인 후 토큰 전체 재발급
     */
    @Transactional
    public Map<String, String> reissueToken(
            String expiredAccessTokenValue,
            String refreshTokenValue
    ) {
        AuthToken expiredAccessToken = createAuthTokenFromAccessTokenValue(expiredAccessTokenValue);
        // 만료된 토큰의 Claim 조회
        Claims expiredTokenClaims = expiredAccessToken.getExpiredTokenClaims();

        String userId = expiredTokenClaims.getSubject();
        UserRoleType roleType = UserRoleType.of(expiredTokenClaims.get("roles", String.class));

        AuthToken refreshToken = createAuthTokenFromRefreshTokenValue(refreshTokenValue);

        // refreshToken 검증
        try {
            refreshToken.validate();
        } catch (InvalidTokenException e) {
            throw new InvalidTokenException(INVALID_REFRESH_TOKEN);
        }

        // userId refresh token 으로 DB 확인
        UserRefreshToken userRefreshToken = userRefreshTokenRepository.findByPersonalIdAndTokenValue(
                userId,
                refreshTokenValue
        ).orElseThrow(() -> new InvalidTokenException(INVALID_REFRESH_TOKEN));

        // 새로운 access token 발급
        AuthToken newAccessToken = createAccessToken(userId, List.of(roleType));

        // Refresh Token Rotation
        // refresh 토큰 설정
        AuthToken newRefreshToken = createRefreshToken(userId, List.of(roleType));
        // DB에 refresh 토큰 업데이트
        userRefreshToken.changeTokenValue(newRefreshToken.getTokenValue());

        return Map.of(
                "accessToken", newAccessToken.getTokenValue(),
                "refreshToken", newRefreshToken.getTokenValue()
        );
    }

    /**
     * 로그아웃 구현을 위해 리프레쉬 토큰 제거
     */
    @Transactional
    public void deleteRefreshToken(String accessToken) {
        AuthToken accessAuthToken = createAuthTokenFromAccessTokenValue(accessToken);
        String userPersonalId = accessAuthToken.getTokenClaims().getSubject();

        // refresh token 삭제
        userRefreshTokenRepository.deleteAllByPersonalId(userPersonalId);
    }

    /**
     * JWT 값을 기반으로 사용자 인가 조회
     */
    public Authentication getAuthentication(AuthToken accessToken) {
        // access token 검증
        accessToken.validate();

        // access token claims 조회
        Claims claims = accessToken.getTokenClaims();
        // claims 값을 기반으로 사용자 Entity 조회
        User user = userRepository.findByPersonalId(claims.getSubject())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 사용자입니다."));
        // claims 값을 기반으로 사용자의 권한 조회
        Collection<GrantedAuthority> authorities = Arrays.stream(new String[]{claims.get("roles").toString()})
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        UserPrincipal userPrincipal = UserPrincipal.from(user, authorities);

        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                accessToken,
                userPrincipal.getAuthorities()
        );
    }

    /**
     * access-token 문자열 값을 기반으로 AuthToken 객체 생성
     */
    public AuthToken createAuthTokenFromAccessTokenValue(String accessToken) {
        return tokenProvider.convertAccessTokenToAuthToken(accessToken);
    }

    /**
     * refresh-token 문자열 값을 기반으로 AuthToken 객체 생성
     */
    public AuthToken createAuthTokenFromRefreshTokenValue(String refreshToken) {
        return tokenProvider.convertRefreshTokenToAuthToken(refreshToken);
    }
}
