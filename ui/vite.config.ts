import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({ root: 'ui', base: process.env.DOLPHIN_BASE_PATH || './', plugins: [react()], build: { target: 'es2022', outDir: 'dist' } });
