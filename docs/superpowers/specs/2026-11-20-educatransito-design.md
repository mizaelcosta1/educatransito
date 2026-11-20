# EDUCATRÂNSITO — Design da primeira versão

## Intenção
Aplicativo Android para agentes de trânsito documentarem ações exclusivamente educativas e preventivas, consultarem o histórico e emitirem comprovantes e relatórios, inclusive sem conexão.

## Objetivos
- Cadastro rápido de orientações, veículo, cidadão e agente.
- Protocolos únicos EDU-AAAA-NNNNNN, gerados localmente e sem reinício diário.
- Consultas por placa, protocolo e cidadão, sem confundir homônimos ou presumir identidade do condutor pelo veículo.
- PDF A4 com três comprovantes por folha, QR Code e aviso de que assinatura confirma recebimento, não confissão.
- Relatórios derivados somente dos registros existentes.
- Backup/restauração protegidos, auditoria de correções e exclusões lógicas.

## Não objetivos
Autuação, gestão de infrações, reconhecimento facial, mapas, sincronização ou integrações externas.

## Abordagem escolhida
Android nativo Kotlin + Compose + Room/SQLite: melhor integração com armazenamento, impressão, biometria e ciclo de vida Android.

## Plataforma
Android mínimo 8.0 (API 26), compile/target API 35. Nome comercial EDUCATRÂNSITO.

## Decisões institucionais pendentes
Prazo de retenção, política de acesso, base legal e regras de compartilhamento devem ser aprovados pela instituição antes do uso real.
