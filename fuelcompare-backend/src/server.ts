import { env } from './config/env.js';
import { buildApplication } from './container.js';
import { logger } from './utils/logger.js';

const { app, anp } = buildApplication({ enableHttpLogging: true });

const server = app.listen(env.PORT, env.HOST, () => {
  logger.info(
    {
      host: env.HOST,
      port: env.PORT,
      environment: env.NODE_ENV,
    },
    'FuelCompare API iniciada',
  );

  // Pré-carrega o levantamento oficial da ANP sem bloquear a API.
  void anp.initialize();
});

server.requestTimeout = 120_000;
server.headersTimeout = 125_000;

let shuttingDown = false;

function shutdown(signal: string): void {
  if (shuttingDown) return;
  shuttingDown = true;
  logger.info({ signal }, 'Encerrando FuelCompare API');

  const forceExit = setTimeout(() => {
    logger.error('Encerramento forçado após 10 segundos');
    process.exit(1);
  }, 10_000);
  forceExit.unref();

  server.close((error) => {
    if (error) {
      logger.error({ err: error }, 'Falha ao encerrar o servidor');
      process.exit(1);
    }
    logger.info('Servidor encerrado');
    process.exit(0);
  });
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('unhandledRejection', (reason) => {
  logger.error({ reason }, 'Promise não tratada');
});
process.on('uncaughtException', (error) => {
  logger.error({ err: error }, 'Exceção não tratada');
  shutdown('uncaughtException');
});
