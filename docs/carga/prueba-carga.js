// Prueba de carga de KIPIPOS con k6 (https://k6.io).
//
// SOLO LECTURA: no crea ventas, así que no emite boletas ni toca el stock. Es seguro correrla contra producción,
// pero usa una empresa de prueba (no la de un cliente real).
//
// Uso:
//   k6 run -e BASE_URL=https://kipiweb-backend.onrender.com -e USUARIO=admin_prueba -e CLAVE='tu-clave' docs/carga/prueba-carga.js
//
// Variables opcionales:
//   PERFIL=suave | normal | fuerte   (por defecto: normal)
//
// Qué mide: cuántos usuarios simultáneos aguanta el backend consultando productos y la salud del servicio.
// El login se hace UNA vez en setup() (el hash de la contraseña es costoso y el login tiene límite de intentos).
//
// Cómo leer el resultado (resumen al final):
//   http_req_duration p(95)  → el 95 % de las respuestas tardó menos que esto. Meta: < 1 s.
//   http_req_failed          → porcentaje de errores. Meta: < 1 %.
//   Si aparece 429 u otros 5xx al subir la carga, ahí está el límite.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const PERFILES = {
  // [duración, usuarios simultáneos objetivo]
  suave:  [['30s', 5],  ['1m', 5],  ['20s', 0]],
  normal: [['30s', 10], ['1m', 25], ['1m', 25], ['20s', 0]],
  fuerte: [['30s', 25], ['1m', 60], ['1m', 60], ['20s', 0]],
};
const perfil = PERFILES[__ENV.PERFIL || 'normal'] || PERFILES.normal;

export const options = {
  stages: perfil.map(([duration, target]) => ({ duration, target })),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  if (!BASE_URL || !__ENV.USUARIO || !__ENV.CLAVE) {
    throw new Error('Faltan BASE_URL, USUARIO o CLAVE. Ver el encabezado del archivo.');
  }
  // Despierta el servicio si está dormido (plan gratis de Render) antes de medir.
  http.get(`${BASE_URL}/api/auth/salud`, { timeout: '90s' });

  const res = http.post(
    `${BASE_URL}/api/usuarios/login`,
    JSON.stringify({ usuario: __ENV.USUARIO, contrasena: __ENV.CLAVE }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s' },
  );
  if (res.status !== 200) {
    throw new Error(`Login falló (HTTP ${res.status}): ${res.body}`);
  }
  return { token: res.json('token') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const salud = http.get(`${BASE_URL}/api/auth/salud`, { tags: { name: 'salud' } });
  check(salud, { 'salud 200': (r) => r.status === 200 });

  const productos = http.get(`${BASE_URL}/api/productos`, { headers, tags: { name: 'productos' } });
  check(productos, { 'productos 200': (r) => r.status === 200 });

  sleep(1 + Math.random() * 2); // un usuario real no dispara peticiones sin pausa
}
