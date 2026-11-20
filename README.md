# EDUCATRÂNSITO — Histórico de Orientações Educativas

Aplicativo Android offline para registro e consulta de ações educativas preventivas no trânsito.

## Plataforma
- Android mínimo: 8.0 (API 26)
- Kotlin + Jetpack Compose
- Room / SQLite local
- Sem permissão de internet no aplicativo

## Estado desta versão
Esta é uma fundação técnica inicial (0.1.0), não uma versão pronta para uso operacional. A interface inicial e o modelo local estão presentes. A gravação de orientações, consultas, PDFs, relatórios, autenticação e backup ainda precisam ser concluídos e testados antes do uso com dados reais.

## Compilação
O workflow do GitHub Actions instala JDK 17, Android SDK e Gradle 8.9, executa os testes e gera um APK debug instalável.

Para compilar localmente com Gradle 8.9 e Android SDK 35:
```bash
gradle testDebugUnitTest assembleDebug
```

O APK de CI fica disponível nos artefatos da execução do workflow.

## Privacidade e uso institucional
Não usar para autuações nem para substituir procedimentos oficiais. A instituição responsável deve aprovar finalidade, base legal, acesso, retenção e compartilhamento de dados antes de inserir dados reais.
