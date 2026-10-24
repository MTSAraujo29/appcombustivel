import { logger } from '../src/utils/logger.js';

// Evita ruído nos testes; os testes que verificam respostas públicas não dependem do log.
logger.level = 'silent';
