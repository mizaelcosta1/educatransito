# Verificação da versão 0.2.0

## Compilação e testes

Ambiente: JDK 17, Gradle 8.9, Android SDK 35 e Build Tools 35.0.0.

```sh
gradle testDebugUnitTest lintDebug assembleDebug --no-daemon
```

O workflow `Android CI` executa os testes, a análise Android Lint e a compilação.
Depois verifica a assinatura do APK com `apksigner`, lê a identificação do pacote
com `aapt` e calcula o SHA-256 do arquivo distribuído. O artefato
`educatransito-debug-apk` contém o APK e esses comprovantes. O artefato
`educatransito-verification` contém resultados de testes, Lint, esquemas Room e
amostras de PDF, quando geradas pelos testes. Resultados de testes JVM/Robolectric
não equivalem à instalação e ao teste em um aparelho físico.

Os testes Android locais usam Robolectric 4.14.1, compatível com API 26 e API 35,
e recursos Android habilitados. Os esquemas Room são exportados para `app/schemas`.
Documentação: [compatibilidade do Robolectric](https://robolectric.org/compatibility_table/)
e [configuração dos testes](https://robolectric.org/getting-started/).

## Conferência no celular

Use dados fictícios durante a validação desta versão de desenvolvimento.

1. Abra o aplicativo sem conexão e cadastre uma orientação com relato.
2. Selecione uma imagem e um vídeo locais pelos botões após o relato.
3. Salve e anote o protocolo apresentado. Feche e reabra o aplicativo.
4. Consulte a orientação no histórico e confira o texto e os anexos.
5. Salve o relatório simples em uma pasta do aparelho e abra o PDF.
6. Salve o relatório detalhado; confira relato completo e imagens. O PDF lista
   os vídeos anexados; a reprodução do vídeo ocorre a partir do registro no app.
7. Confira relatórios com vários registros, relato longo e intervalo sem registros.
8. Cancele o seletor de arquivos e o destino do PDF: o app deve continuar utilizável
   sem confirmar como concluída uma operação cancelada.

## Instalação e assinatura

O APK `debug` é destinado à avaliação. O pacote é `br.com.educatransito`,
com Android mínimo 8.0 (API 26). Esta compilação não configura uma chave privada
institucional de distribuição.

Runners novos do GitHub podem gerar certificados de depuração diferentes. Se o
Android recusar atualizar uma versão anterior por divergência de assinatura,
preserve os dados existentes. A desinstalação remove o banco e os anexos internos.
Para atualizações institucionais é necessário configurar uma chave de assinatura
persistente e privada, guardada fora do repositório público.
