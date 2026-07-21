# Activar SonarCloud (paso final, manual)

El workflow `.github/workflows/ci.yml` ya corre build + test + cobertura JaCoCo
en cada push/PR, y ya intenta el análisis de SonarCloud — pero lo salta
automáticamente hasta que existan estos 3 valores en el repositorio de GitHub
(no se pueden crear desde el código, requieren tu cuenta):

1. Entra a https://sonarcloud.io e inicia sesión con tu cuenta de GitHub.
2. "+" → "Analyze new project" → selecciona `brandoeci/CitySim`.
3. Anota:
   - **Organization key** (la de tu cuenta/equipo en SonarCloud)
   - **Project key** (normalmente `brandoeci_CitySim` o similar)
4. En SonarCloud: My Account → Security → genera un **token**.
5. En GitHub, en el repo → Settings → Secrets and variables → Actions:
   - **Secrets** → `New repository secret` → `SONAR_TOKEN` = el token del paso 4.
   - **Variables** → `New repository variable` → `SONAR_ORGANIZATION` = organization key,
     `SONAR_PROJECT_KEY` = project key.
6. Vuelve a correr el workflow (push cualquier commit, o "Re-run jobs" en Actions).
   A partir de ahí el paso "SonarCloud Scan" deja de saltarse y el análisis
   aparece en tu dashboard de SonarCloud.

No hace falta tocar `ci.yml` ni el `pom.xml` para esto — todo lo demás ya está
armado.
