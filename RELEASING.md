# Releasing path-parser

Releases publiseres til Maven Central via Sonatype Central Portal
(`central-publishing-maven-plugin`).

## Forutsetninger

1. **Central Portal-konto** med rettigheter til namespace `org.brylex`.
   Generer en bruker-token under <https://central.sonatype.com/account>.

2. **GPG-nøkkel** registrert hos en offentlig keyserver (eks. `keys.openpgp.org`).
   Nøkkel-ID må kunne resolves av `gpg`.

3. **`~/.m2/settings.xml`** med credentials:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>${CENTRAL_TOKEN_USER}</username>
         <password>${CENTRAL_TOKEN_PASSWORD}</password>
       </server>
     </servers>
   </settings>
   ```

   `id` må matche `<publishingServerId>` i `pom.xml` (`central`).

## Multi-modul-merknad

Fra og med 3.0 består prosjektet av to publiserte moduler:

- `path-parser` — runtime (consumer-dependency)
- `path-parser-processor` — APT-codegen (`<scope>provided</scope>` hos
  konsumenten)

`maven-release-plugin` med `autoVersionSubmodules` slipper begge samtidig.
Begge må være tilgjengelige i Central før release-PR-en merges, ellers
brytes konsumerende builds.

`examples/native-image-smoke/` er **ikke** del av reactoren og skal ikke
publiseres — den er kun for CI-verifisering av native-image-banen.

## Pre-release-sjekkliste

- [ ] `master` har det som skal slippes
- [ ] `mvn clean verify` grønn (alle moduler)
- [ ] CI `build` grønn på den committen vi tagger
- [ ] CI `native-image`-jobben grønn på samme commit
- [ ] `examples/native-image-smoke` kjørt lokalt med GraalVM 21 (manuell
      smoke om CI-jobben av en grunn ikke har kjørt)
- [ ] CHANGELOG/release notes oppdatert (om relevant)

## Slipp-prosedyre

Erstatt `X.Y.Z` med den faktiske versjonen i kommandoene under
(eksempelet bruker `3.0.0` → `3.0.1-SNAPSHOT`).

1. **Bytt versjon** fra `X.Y.Z-SNAPSHOT` til `X.Y.Z`:

   ```sh
   mvn versions:set -DnewVersion=3.0.0 -DprocessAllModules=true -DgenerateBackupPoms=false
   git commit -am "release 3.0.0"
   ```

   `-DprocessAllModules=true` er nødvendig i multi-modul: uten den
   oppdateres KUN parent-pom og barnemodulene står igjen som
   `X.Y.Z-SNAPSHOT`.

2. **Bygg og signer** lokalt for å verifisere:

   ```sh
   mvn -Prelease clean verify
   ```

   Produserer `path-parser-3.0.0.jar`, `-sources.jar`, `-javadoc.jar`,
   `path-parser-processor-3.0.0.jar` (+ tilsvarende sources/javadoc), og
   tilhørende `.asc`-signaturer i `target/` i hver modul.

3. **Tagg og push**:

   ```sh
   git tag -s path-parser-3.0.0 -m "release 3.0.0"
   git push origin master --tags
   ```

4. **Deploy** til Central:

   ```sh
   mvn -Prelease deploy
   ```

   `autoPublish=true` i pom-en gjør at bundelen automatisk publiseres etter
   validering. Følg framdrift på <https://central.sonatype.com/publishing>.

5. **Bump til neste snapshot**:

   ```sh
   mvn versions:set -DnewVersion=3.0.1-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
   ```

   `examples/native-image-smoke/pom.xml` er **utenfor reactoren** og må
   bumpes manuelt — oppdater `<pp.version>`-propertyen til samme nye
   verdi, ellers feiler native-image-CI-jobben på neste master-push:

   ```sh
   sed -i '' 's|<pp.version>.*</pp.version>|<pp.version>3.0.1-SNAPSHOT</pp.version>|' examples/native-image-smoke/pom.xml
   git commit -am "prepare next development iteration"
   git push origin master
   ```

## Hvis noe går galt

- **Signering feiler:** sjekk at `gpg --list-secret-keys` viser nøkkelen og
  at agent kjører. `--pinentry-mode loopback` er allerede satt i pom-en for
  ikke-interaktiv signering.

- **Deploy feiler validering:** Central krever sources-jar, javadoc-jar,
  signaturer og komplett POM (lisens, scm, developers). Alt er konfigurert i
  pom-en — feilmeldingen i Central-portalen forteller hvilket krav som mangler.

- **Versjonen er allerede deployet:** Central tillater ikke overskriving. Bump
  patch-versjon og prøv igjen.
