import { defineConfig } from 'vite';

const target = process.env.API_TARGET ?? 'http://127.0.0.1:8081';
const proxy = Object.fromEntries(['/api', '/actuator', '/v3', '/swagger-ui', '/oauth2', '/login/oauth2'].map(path => [path, {target}]));
export default defineConfig({server:{proxy},preview:{proxy}});
