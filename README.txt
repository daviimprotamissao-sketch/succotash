xTra Player 2.2 - Autoria: xTra Soluções (Android, Kotlin + Jetpack Compose + Media3 1.11.1)

Recursos: provedor Xtream Codes (servidor/usuário/senha) ou lista M3U, canais agrupados por
emissora (Band (3) > Band, Band Sports...), filmes e séries com preview na tela inicial,
perfis, perfil infantil e controle parental (PIN) para conteúdo +18, favoritos.

ABAS: Início, TV ao vivo, Filmes, Séries, Playlists (mostra o MAC do aparelho e sincroniza com o
site — veja a pasta cloud-playlists/ na raiz deste projeto), Perfis (sua própria aba, fora de
Ajustes) e Ajustes (controle parental, PIN e formato de tela padrão).

IMPORTANTE: para a aba Playlists sincronizar com o site, configure CLOUD_DB_URL em Data.kt com a
URL do seu Firebase Realtime Database — passo a passo completo em cloud-playlists/README.md.

Como gerar o APK:
1) Suba esta pasta para um repositório no GitHub.
2) Aba Actions > "Build APK" > Run workflow (ou só dê push).
3) Baixe o artefato "iptv-player-apk" (app-debug.apk) e instale no celular.
Ou abra a pasta no Android Studio e rode.

Lista padrão (TV aberta): app/src/main/assets/playlist.txt

Telas no player (botão ⛶ no canto superior direito): Ajustar, Esticar (tela toda),
4:3 (800×600), Preencher (zoom) e Preencher 4K (zoom + melhor qualidade até 3840x2160).

Versão 2.2 — limpeza, desempenho e novo player

POR QUE FECHAVA ("xTra Player apresenta falhas constantes")
- A lista do Xtream era baixada inteira como uma String gigante e depois convertida em JSONArray.
  Em provedores grandes (dezenas de milhares de canais/filmes) isso estourava a memória do app.
  Como OutOfMemoryError não é "Exception", o try/catch não pegava e o Android fechava o app.
  Agora a resposta é lida em streaming (android.util.JsonReader), guardando só os campos usados,
  o app pede heap maior (largeHeap) e qualquer erro vira mensagem na tela em vez de crash.
- Dois canais com a mesma URL geravam chave repetida na lista (crash do Compose). Ids agora são únicos.
- Trocar de provedor/recarregar várias vezes iniciava vários carregamentos ao mesmo tempo. Agora o
  anterior é cancelado.

POR QUE ERA LENTO
- Regex era recompilado a cada canal (Brand.norm/clean e Live.display). Agora é compilado uma vez e
  o texto de busca de cada canal é calculado uma vez só.
- Agrupar, filtrar e buscar rodava na thread principal. Agora roda em segundo plano, e a busca só
  filtra 160 ms depois de parar de digitar.
- Auth, categorias e canais eram baixados um depois do outro. Agora vão em paralelo; filmes e séries
  baixam em segundo plano sem travar os canais.
- Cache em disco: na 2ª abertura a lista aparece na hora e atualiza por trás.
- Papel de parede de 2 MB (2560x1440) borrado em tempo real a cada quadro -> WebP 1280x720 já borrado (~7 KB).
- Um player de prévia por canal clicado -> um único player reaproveitado, com 250 ms de espera e
  limite de 480p; buffers menores para o canal abrir mais rápido.
- Coil (logos/pôsteres) com cache de memória e disco dimensionados.
- IMPORTANTE: Compose em build DEBUG é bem mais lento. Use o APK "release" (o workflow
  .github/workflows/build.yml já gera app-release.apk, com R8 + redução de recursos).
  Local: gradle :app:assembleRelease

VISUAL
- Fonte: Helvetica. A Helvetica é comercial e não pode ser distribuída sem licença, então o app usa
  os arquivos da SUA cópia se você colocar Helvetica-Regular.ttf, Helvetica-Bold.ttf (e opcional
  Helvetica-Light.ttf) em app/src/main/assets/fonts/. Sem eles, usa Liberation Sans (OFL), quase
  idêntica. Inter foi removida (-3 MB).
- Aba Canais redesenhada: grupos com fundo degradê preto e branco em movimento (uma única animação
  compartilhada, só redesenha), busca em pílula com limpar, canais com número + logo, prévia 16:9 com
  selo AO VIVO, guia (EPG) com barra de progresso do programa atual, dias em chips, transições
  suaves entre grupos e canais.

PLAYER
- Sem nome do canal no topo, sem botão de ajustes no topo, sem pausar, sem +15 s / -5 s.
- Barra inferior que só aparece ao tocar na tela (ou apertar qualquer botão do controle) e some
  sozinha após 4 s: logo do canal, número, Ajustes (formato da tela) e Expandir tela.
- Filmes/episódios têm apenas uma barra de progresso fina na mesma barra (canal ao vivo não).
- Voltar: botão/gesto Voltar do Android.

Lista padrão (TV aberta): app/src/main/assets/playlist.txt
