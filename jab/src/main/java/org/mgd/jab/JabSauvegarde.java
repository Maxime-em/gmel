package org.mgd.jab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mgd.jab.dto.Dto;
import org.mgd.jab.dto.adapter.ClassAdapter;
import org.mgd.jab.dto.adapter.LocalDateAdapter;
import org.mgd.jab.dto.adapter.PathAdapter;
import org.mgd.jab.objet.Jo;
import org.mgd.jab.persistence.Jao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Classe utilisée instanciée par {@link JabSingletons} et utilisée par {@link Jao} pour agréger des données durant la
 * sauvegarde d'un objet métier de type {@link Jo}.
 *
 * @author Maxime
 */
public class JabSauvegarde {
    public static final Gson gsonSauvegarde = new GsonBuilder().enableComplexMapKeySerialization()
            .registerTypeHierarchyAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeHierarchyAdapter(Path.class, new PathAdapter())
            .registerTypeHierarchyAdapter(Class.class, new ClassAdapter())
            .create();
    private static final Logger LOGGER = LogManager.getLogger(JabSauvegarde.class);
    private final Moniteur moniteur = new Moniteur();
    private final JabCreation creation;
    private final Map<Path, String> sources = new ConcurrentHashMap<>();
    private int nombreThreads;
    private boolean asynchrone;
    private long delai;

    public JabSauvegarde() {
        this.creation = JabSingletons.creation();
    }

    public int getNombreThreads() {
        return nombreThreads;
    }

    public void setNombreThreads(int nombreThreads) {
        this.nombreThreads = nombreThreads;
    }

    public boolean isAsynchrone() {
        return asynchrone;
    }

    public void setAsynchrone(boolean asynchrone) {
        this.asynchrone = asynchrone;
    }

    public long getDelai() {
        return delai;
    }

    public void setDelai(long delai) {
        this.delai = delai;
    }

    public void disposer() {
        moniteur.arreter();
    }

    public void reinitialiser() {
        if (moniteur.demarrer) {
            moniteur.arreter();
            moniteur.attendre();
        }
        sources.clear();
        moniteur.demarrer();
    }

    public void attendre() {
        moniteur.attendre();
    }

    public <O extends Jo> void demarrer(O objet) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Démarrage de la sauvegarde de l'objet {}", objet);
        }
        if (objet.getIdentifiant() != null && creation.verifier(objet.getIdentifiant())) {
            Dto dto = creation.getJao((objet.getIdentifiant())).dto(objet);
            dto.setIdentifiant(objet.getIdentifiant().toString());
            sources.put(creation.getFichier(objet.getIdentifiant()), gsonSauvegarde.toJson(dto));
        }

        if (asynchrone) {
            moniteur.executer(new ProcessusExecutionSauvegarde(moniteur), delai);
        } else {
            ecrire();
        }
    }

    public synchronized void ecrire() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Écriture des fichiers {}", sources.keySet());
        }
        sources.forEach((chemin, contenu) -> {
            try {
                Files.writeString(chemin, contenu);
            } catch (IOException e) {
                LOGGER.error(e);
            }
        });
        sources.clear();
    }

    private record ProcessusExecutionSauvegarde(Moniteur moniteur) implements Runnable {
        @Override
        public void run() {
            if (moniteur.tachesCompletesSaufUne()) {
                JabSingletons.sauvegarde().ecrire();
            }
        }
    }

    private class Moniteur {
        private final List<Future<?>> futures = new CopyOnWriteArrayList<>();
        private ScheduledThreadPoolExecutor executeur;
        private boolean demarrer = false;

        public void demarrer() {
            executeur = new ScheduledThreadPoolExecutor(nombreThreads);
            futures.clear();
            demarrer = true;
        }

        public void executer(Runnable commande, long delai) {
            futures.add(executeur.schedule(commande, delai, TimeUnit.MILLISECONDS));
        }

        public void arreter() {
            demarrer = false;
            executeur.shutdown();
        }

        public void attendre() {
            futures.forEach(scheduledFuture -> {
                try {
                    scheduledFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.warn(e);
                    Thread.currentThread().interrupt();
                }
            });
        }

        public boolean tachesCompletesSaufUne() {
            return executeur.getTaskCount() - executeur.getCompletedTaskCount() == 1;
        }
    }
}
