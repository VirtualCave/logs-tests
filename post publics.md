# Value: La importancia de la observabilidad

Hay fallos que no rompen el código, pero sí impactan a nivel de negocio. Un servicio de pagos procesando transacciones fallidas durante horas sin que nadie se entere. Un incidente que debería resolverse en minutos y acaba siendo una investigación de tres días. Una multa regulatoria por datos de clientes que se colaron en un log que nadie pensó revisar. Todos tienen algo en común: los sistemas funcionaban. Los logs, no.

### Impacto directo en negocio

* **Detección tardía de incidentes:** Sin las señales correctas, los equipos operativos vuelan a ciegas. Cada hora sin visibilidad es una hora de impacto no contenido.
* **SLAs rotos y clientes afectados:** Cuando la trazabilidad falla, investigar un incidente entre servicios pasa de minutos a días, con el coste reputacional que eso conlleva.
* **Exposición legal y regulatoria:** Datos sensibles que se cuelan en logs pueden derivar en auditorías, sanciones GDPR o PCI-DSS y procesos de remediación costosos.
* **Costes operativos disparados:** Un exceso de logs en producción puede multiplicar la factura de observabilidad de un mes sin que nadie lo haya planificado.

La observabilidad no es un problema de infraestructura. Empieza en el código y se puede validar antes de llegar a producción.

¿Quieres dejar de descubrir estos problemas en producción?** Hemos publicado un tutorial práctico. Si trabajas con Java y Spring Boot, está escrito para ti.

---

# Técnico: Validación de logs con JUnit 5

### Validación de logs en tests con JUnit 5, Spring Boot y Logback

La mayoría de los tests verifican lo que devuelve el código. Pocos verifican lo que registra. Y sin embargo, los logs son parte del contrato de tu sistema: las alertas dependen de que el nivel sea correcto, los SREs de que el `traceId` esté presente, el equipo legal de que ningún payload sensible se cuele donde no debe. Si eso no se testa, se rompe en silencio.

En este tutorial mostramos cómo capturar y validar los logs de tu aplicación directamente en los tests usando `ListAppender` de Logback. Cubrimos las aserciones que más importan:

* **Nivel del log**
* **Mensajes con partes dinámicas**
* **Contexto de traza en MDC**
* **Excepciones registradas**
* **Orden de eventos y ausencia de PII**

Todo con utilidades helper que eliminan el boilerplate y ejemplos listos para usar. El enfoque combina pruebas funcionales del endpoint con pruebas de observabilidad en la misma suite. Porque un sistema puede responder correctamente y aun así estar completamente ciego cuando algo falla.

---

# Estudios de Caso

### EQUIFAX: El impacto real de la mala gestión de logs

147 millones de personas. Datos expuestos durante 76 días, y nadie lo detectó a tiempo. El sistema de inspección de tráfico de red de Equifax llevaba inactivo 19 meses por un certificado de seguridad caducado. Cuando lo renovaron, detectaron el tráfico malicioso de inmediato. 

Dos años después del incidente, Equifax había acumulado 1.350 millones de dólares en costes. Sus sistemas tenían herramientas, aunque el problema era que nadie sabía que habían dejado de funcionar. Eso es lo que ocurre cuando la observabilidad no se valida: todo parece estar monitorizando, pero en realidad está ciego.

### CAPITAL ONE: La falta de monitorización activa

100 millones de clientes afectados. La forma en que se descubrió el incidente dice más sobre el problema que el incidente en sí: no fue el equipo interno quien lo detectó, sino una alerta externa. Los logs existían pero no se monitorizaron activamente en busca de anomalías. 

El impacto financiero total rozó los 300 millones de dólares, más una multa de 80 millones impuesta por el regulador bancario. La conclusión del postmortem fue clara: la monitorización continua y las auditorías automatizadas podrían haber detectado la actividad anómala antes de que fuera explotada.

### GITLAB: La falsa sensación de seguridad

GitLab hizo algo inusual cuando sufrió uno de los incidentes operativos más sonados de 2017: lo transmitió en directo. Un ingeniero ejecutó accidentalmente un comando de borrado en el servidor de base de datos principal. El sistema de backups existía pero no se probaba regularmente. Cuando llegó el momento de usarlo, los snapshots estaban incompletos, corruptos o eran inutilizables. Nadie lo sabía porque nadie lo había verificado. 

Un sistema que no se valida activamente da una falsa sensación de seguridad. Puedes creer que tienes observabilidad y no tenerla en el momento que más importa.

---

> **¿Trabajas con Java y Spring Boot?** Hemos publicado tutoriales prácticos para equipos que quieren detectar estos problemas antes de que sea tarde. ¡Revisa nuestra guía completa!
