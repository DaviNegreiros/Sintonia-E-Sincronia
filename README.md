# ApenasDance-AiMotionTrackingDanceGame

Base Python modular para o motor offline do ApenasDance.

## Arquitetura

- `contracts/`: contratos imutaveis e versionados consumidos pela engine e por clientes futuros.
- `config/`: configuracoes centralizadas de timing, feedback, score e sessao.
- `domain/`: regras puras de pose, avaliacao, feedback, scoring, ranking e timeline.
- `engine/`: API publica `GameEngine v1` e orquestracao de sessoes.
- `vision/`: integracoes de camera, video e MediaPipe para Python.
- `storage/`: fronteira de persistencia e leitura local de movesets.
- `observability/`: logs estruturados de eventos e frames de sessao.
- `tests/`: testes de comportamento para proteger refatoracoes e futuras portas Android.
- `core/`: camada de compatibilidade para imports antigos.

Os notebooks da raiz nao sao parte ativa desta branch. Eles podem ser usados apenas como referencia historica; regras de negocio devem viver nos modulos Python acima.

## API publica

```python
from engine import GameEngine

engine = GameEngine()
moveset = engine.load_dance("dance_moveset.json")
session = engine.create_session(moveset)
session.start()
```

O aplicativo Android futuro deve consumir a engine por contratos/estados/eventos, sem depender de notebooks ou de detalhes internos de visao computacional.
