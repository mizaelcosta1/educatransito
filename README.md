# EDUCATRÂNSITO — Histórico de Orientações Educativas

Aplicativo Android nativo em Kotlin e Jetpack Compose para documentar ações educativas e preventivas. Android mínimo: **8.0 (API 26)**. Versão de desenvolvimento: **0.2.0**.

## Recursos desta versão

- **Salvar orientação** no banco SQLite/Room, com protocolo EDU-AAAA-000001, data e horário automáticos.
- Cadastro de veículo, cidadão opcional, local, público, orientação principal, orientações complementares, relato e observações.
- Botões **Anexar imagem** e **Anexar vídeo** imediatamente após o relato.
- Cópia dos arquivos escolhidos para o armazenamento privado do aplicativo: continuam acessíveis offline mesmo se a origem for removida.
- Até 10 anexos por registro; imagens até 20 MiB e vídeos até 200 MiB. Arquivos incompatíveis, vazios ou acima do limite são recusados.
- Consulta por placa completa, nome ou protocolo; detalhes e anexos dos registros salvos.
- Inclusão posterior de anexos sem trocar protocolo/data, com evento de auditoria.
- Abertura do anexo em um visualizador Android e **Salvar cópia** para uma pasta escolhida.
- **Salvar relatório simples** em PDF A4: totais por público/categoria e histórico cronológico.
- **Salvar relatório detalhado** em PDF A4: dados, relato integral, observações, imagens e identificação dos vídeos.
- Relatórios de uma orientação ou por dia, semana (segunda a domingo), mês, período personalizado ou todos os registros.
- Os botões de relatório no formulário primeiro salvam a orientação. Cancelar a escolha do arquivo PDF não remove o registro.
- Migração não destrutiva da estrutura de banco 1 para 2.

Vídeos ficam anexados ao registro e são abertos pelo histórico. O PDF apresenta seus nomes, tipos e tamanhos; não contém um player ou o arquivo de vídeo. Use **Salvar cópia** para guardar os vídeos separadamente.

## Como usar

1. Abra **NOVA ORIENTAÇÃO**.
2. Informe local, orientação principal e relato. Os dados de identificação são opcionais.
3. Após o relato, toque em **ANEXAR IMAGEM** ou **ANEXAR VÍDEO** e escolha um arquivo local.
4. Toque em **SALVAR ORIENTAÇÃO**, ou em um dos dois botões de relatório para salvar e exportar.
5. Em **HISTÓRICO DE ORIENTAÇÕES**, abra o protocolo para consultar o relato, abrir os arquivos, guardar cópias ou adicionar anexos.
6. Em **FICHAS E RELATÓRIOS**, escolha o período e salve o PDF simples ou detalhado no seletor de arquivos Android.

## Integridade e limites

Protocolo, cadastro, anexos e auditoria são gravados numa única transação. A sequência não reinicia diariamente e permanece após fechar o aplicativo. A unicidade cobre o banco desta instalação; instalações independentes ainda não possuem namespace institucional/sincronização.

Nomes iguais não são mesclados automaticamente: cada novo registro identificado cria uma identificação local distinta. Esta versão não fornece vinculação verificada de uma mesma pessoa entre abordagens. Os totais por público contam **atendimentos registrados**, não pessoas únicas. Marca, modelo e cor são preservados no próprio registro histórico.

A aplicação não declara permissão de internet nem permissões amplas de galeria/armazenamento. Seleciona arquivos pelo sistema e mantém a cópia interna. O banco e os arquivos estão protegidos pelo sandbox Android, mas **ainda não há senha/biometria no aplicativo, criptografia própria do banco/anexos ou backup criptografado/restauração**.

A versão é destinada à avaliação com dados fictícios até a implementação dos controles de acesso e da política institucional. A finalidade é exclusivamente educativa; o histórico da placa não identifica o condutor de abordagens anteriores.

### Próximos recursos do projeto original

- Controle de acesso por senha/biometria e proteção criptográfica.
- Backup/restauração incluindo anexos.
- Correção e exclusão lógica pela interface, com histórico de alterações.
- Comprovantes de recebimento com três fichas por A4 e QR Code.
- Impressão direta e gestão institucional de categorias/conservação.

## Compilação e verificação

Requisitos: **JDK 17, Gradle 8.9, Android SDK 35 e Build Tools 35.0.0**.

```bash
gradle testDebugUnitTest lintDebug assembleDebug --no-daemon
```

O workflow [Android CI](https://github.com/mizaelcosta1/educatransito/actions/workflows/android.yml) compila, executa testes JVM/Robolectric e Lint, verifica a assinatura com apksigner, e publica:

- educatransito-debug-apk: APK instalável e SHA-256.
- educatransito-verification: resultados de testes, Lint, metadados, esquemas Room e PDFs fictícios para inspeção.

APK local: app/build/outputs/apk/debug/app-debug.apk.

Testes cobrem transações, concorrência de protocolos, migração, reabertura do banco, homônimos, snapshots do veículo, importação e limites dos anexos, períodos, contagens e paginação de PDF. Consulte [VERIFICACAO.md](docs/VERIFICACAO.md) para o roteiro no aparelho e limitações de assinatura de desenvolvimento.

Não distribua o APK debug como versão institucional final. A chave de assinatura de distribuição definitiva ainda precisa ser estabelecida.
