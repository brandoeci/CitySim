# SonarCloud

Este repo (`brandoeci/CitySim`, público) está conectado a SonarCloud en la
organización `brandoeci` con **Automatic Analysis**: SonarCloud escanea el
código directo desde GitHub cada vez que hay cambios, sin pasar por el
pipeline de CI.

- Dashboard: https://sonarcloud.io/project/overview?id=brandoeci_CitySim
- Organization key: `brandoeci`
- Project key: `brandoeci_CitySim`

## Por qué no está integrado en `ci.yml`

SonarCloud no permite tener **Automatic Analysis** y **análisis vía CI**
(sonar-scanner corriendo dentro de un workflow) activos al mismo tiempo para
el mismo proyecto — hay que elegir uno. Como el automático ya corre solo y
sin mantenimiento, se dejó ese como el mecanismo activo, y el paso de Sonar
se sacó de `ci.yml` para evitar el conflicto.

Si en el futuro se prefiere el análisis vía CI (por ejemplo, para bloquear
un PR si el Quality Gate falla), hay que:
1. Desactivar Automatic Analysis: en SonarCloud → Administration → Analysis Method.
2. Agregar en GitHub (Settings → Secrets and variables → Actions):
   - Secret `SONAR_TOKEN` (Access Tokens, en la cuenta de SonarCloud).
   - Variables `SONAR_ORGANIZATION=brandoeci` y `SONAR_PROJECT_KEY=brandoeci_CitySim`.
3. Volver a agregar el paso `sonar-maven-plugin:sonar` en el job `backend` de `ci.yml`.

## Warning de codificación

El primer análisis mostró "There are problems with file encoding in the
source code" — es un aviso menor (probablemente algún archivo con acentos/ñ
en una codificación distinta a UTF-8), no bloqueó el análisis ni afecta las
métricas mostradas.
