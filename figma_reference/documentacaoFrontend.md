Sintonia & Sincronia — Documentação do Frontend
Estrutura geral
App mobile em React com proporção fixa 9:16 centralizado no viewport. Navegação por estado React (useState) — sem biblioteca de rotas. Três estados de página + dois overlays empilhados dentro do container.

App (shell 9:16)
├── HomeScreen
├── NovaDancaScreen
├── DancasSalvasScreen
│   ├── DancaDetailModal   (overlay z-10, slide-up)
│   │   └── [confirmação de deleção]  (overlay z-20, blur)
│   └── DancandoOverlay    (overlay z-20, countdown)
Páginas
1. Home ("home")
Tela inicial. Logo SS_Logo.png com glow roxo ocupa 25% da altura. Abaixo, divisor hairline e dois botões:

ⓘ Nova Dança — ícone de informação à esquerda (fora do botão). Ao clicar no ⓘ abre popover com 4 dicas de gravação (fecha ao clicar fora). O botão navega para nova-danca.
Danças Salvas — navega para dancas-salvas.
Decoração: 6 notas musicais flutuantes animadas em loop com floatNote.

2. Nova Dança ("nova-danca")
Formulário de criação. Botão < no canto superior esquerdo volta ao Home.

Logo menor (scale 0.72) no topo com glow
Input "Nome da dança" — borda roxa, focus state iluminado
Botão de vídeo 9:16 — área tracejada com + central. Ao clicar abre seletor de arquivo (<input type="file" accept="video/*">). Após seleção, exibe preview do vídeo com URL.createObjectURL. Ícone de troca aparece no canto do preview.
Botão Criar — pill centralizado no rodapé. Desabilitado/apagado enquanto o nome está vazio; ativa com glow roxo quando preenchido.
3. Danças Salvas ("dancas-salvas")
Galeria. Botão < + título no header. Lista vem do estado dances[] em App (começa com 7 placeholders).

Cards 9:16 em grid 2 colunas, cada um com:

Gradiente roxo único como fundo
Silhueta SVG de dançarino
Nome da dança no rodapé
"Melhor Ranque [badge]" — badge colorido com a letra do ranque. Cores por letra: S=dourado, A+=laranja, A=verde, B=azul, C=lilás, D/E=vermelho, ?=cinza
Hover: eleva o card (scale), revela ícone de play
Se todas as danças forem deletadas, exibe mensagem "Nenhuma dança salva".

Overlays
DancaDetailModal (slide-up sobre Danças Salvas)
Abre ao clicar em qualquer card. Entra com animação translateY(100%) → 0.

Header:

< fecha o modal e volta à galeria
Nome da dança
🗑 Ícone lixeira vermelho neon no canto direito — ao clicar abre sub-overlay de confirmação
Preview 9:16: mesma estética do card, com botão Play/Pause central. Ao clicar Play, barras de áudio animadas aparecem na base do preview.

Botão Dançar: pill roxo no rodapé. Abre o DancandoOverlay.

Sub-overlay de confirmação de deleção:

Blur sobre o modal
Ícone lixeira com glow vermelho, texto pedindo confirmação com nome da dança em destaque
Cancelar (fecha o popup) / Apagar (remove a dança do array e fecha tudo)
DancandoOverlay (sobre tudo, z-20)
Ativado pelo botão Dançar. Três fases em sequência automática:

Fase	Duração	Visual
countdown	10 s	Número grande (10→0) com animação countPop por tick + anel SVG depletando suavemente via stroke-dashoffset + texto "Prepare-se"
go	0,7 s	Flash "VAI!" com glow na cor do ranque
playing	indefinido	Preview 9:16 do card com barras de áudio animadas + legenda "Reproduzindo · [nome]"
X no canto superior direito disponível em todas as fases — fecha o overlay e o modal, voltando diretamente à galeria.

Componentes compartilhados
Componente	Função
Logo	Imagem SS_Logo.png com mix-blend-mode: screen (remove fundo branco) + glow radial atrás
Hairline	Divisor de 1px com gradiente roxo transparente nas extremidades
BackBtn	Botão < com hover state, reutilizado em todas as telas
Dancer	Silhueta SVG de figura humana, colorida pela ring color de cada dança
InfoPopover	Popover com lista de dicas, fecha via mousedown fora do elemento
PillBtn	Botão dos menus home com ícone, label e chevron — variante primary/secondary
Paleta e tokens visuais
Fundo: #080014 (shell) → #160730→#0c051e (gradiente interno)
Primário: #7c3aed / #5b21b6
Texto: #f0eaff, #e9d5ff, #c4b5fd
Bordas: rgba(124,58,237,0.2–0.5)
Vermelho destrutivo: #ff2d55 com glow
Fonte display: Outfit 600–900
Fonte corpo: DM Sans 400–500