package br.com.rdrtech.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP de quem fez a requisição. O backend não publica porta: só o nginx do frontend chega nele, e
 * o nginx grava o IP real em {@code X-Real-IP}. Por isso o cabeçalho é confiável aqui.
 */
public final class IpCliente {

    private IpCliente() {
    }

    public static String de(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip.trim();
    }
}
