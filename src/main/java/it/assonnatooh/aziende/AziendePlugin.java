package it.assonnatooh.aziende;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class AziendePlugin extends JavaPlugin {
    private CompanyStore store;
    private final Map<UUID, Company> deleteConfirmations = new HashMap<>();
    private final Map<UUID, Contract> contracts = new HashMap<>();

    @Override public void onEnable() {
        store = new CompanyStore(this);
        getCommand("aziendeadmin").setExecutor(new AdminCommands());
        getCommand("azienda").setExecutor(new CompanyCommands());
        getCommand("aziendeadmin").setTabCompleter(new AdminCommands());
        getCommand("azienda").setTabCompleter(new CompanyCommands());
        getLogger().info("AziendePlugin attivato.");
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(Component.text("[Aziende] " + text, NamedTextColor.YELLOW));
    }

    private void info(CommandSender sender, Company company) {
        msg(sender, "Informazioni azienda: " + company.name);
        msg(sender, "Fondo: " + String.format(Locale.ITALY, "%.2f", company.fund));
        msg(sender, "Direttore: " + (company.directorName.isBlank() ? "Non assegnato" : company.directorName));
        String vice = company.employees.values().stream()
                .filter(e -> e.rank.equalsIgnoreCase("ViceDirettore"))
                .map(e -> e.name).findFirst().orElse("Non assegnato");
        msg(sender, "Vicedirettore: " + vice);
        if (company.employees.isEmpty()) {
            msg(sender, "Dipendenti: nessuno");
        } else {
            String employees = company.employees.values().stream()
                    .sorted(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)))
                    .map(e -> e.name + " (" + e.rank + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("nessuno");
            msg(sender, "Dipendenti: " + employees);
        }
    }

    private Company actorCompany(Player player) {
        Company company = store.companyOf(player.getUniqueId());
        if (company == null) return null;
        Employment employment = company.employees.get(player.getUniqueId());
        if (player.getUniqueId().equals(company.director) ||
                (employment != null && employment.rank.equalsIgnoreCase("ViceDirettore"))) return company;
        return null;
    }

    private class AdminCommands implements CommandExecutor, TabCompleter {
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("aziende.admin")) { msg(sender, "Non hai il permesso."); return true; }
            if (args.length == 0) { msg(sender, "Uso: /aziendeadmin create <nome> | elimina <nome> | pex <nick> <azienda> | info <azienda>"); return true; }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create", "crea" -> {
                    if (args.length != 2 || !args[1].matches("[A-Za-z0-9_-]{1,32}")) { msg(sender, "Uso: /aziendeadmin create <nome> (solo lettere, numeri, _ e -, massimo 32 caratteri)."); return true; }
                    if (store.get(args[1]) != null) { msg(sender, "Esiste già un'azienda con questo nome."); return true; }
                    store.create(args[1]); msg(sender, "Azienda " + args[1] + " creata.");
                }
                case "elimina", "delete" -> {
                    if (args.length != 2) { msg(sender, "Uso: /aziendeadmin elimina <nome>"); return true; }
                    Company company = store.get(args[1]);
                    if (company == null) { msg(sender, "Azienda non trovata."); return true; }
                    if (!(sender instanceof Player player)) { store.delete(company); msg(sender, "Azienda eliminata."); return true; }
                    deleteConfirmations.put(player.getUniqueId(), company);
                    msg(sender, "Sei sicuro di eliminare l'azienda " + company.name + "?");
                    player.sendMessage(Component.text("[Conferma] ", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/aziendeadmin elimina-conferma"))
                            .append(Component.text("[Rifiuta]", NamedTextColor.RED)
                                    .clickEvent(ClickEvent.runCommand("/aziendeadmin elimina-rifiuta"))));
                }
                case "elimina-conferma" -> {
                    if (!(sender instanceof Player player)) { msg(sender, "Comando disponibile solo in gioco."); return true; }
                    Company pending = deleteConfirmations.remove(player.getUniqueId());
                    if (pending == null || store.get(pending.name) == null) { msg(sender, "Non hai un'eliminazione da confermare."); return true; }
                    store.delete(pending); msg(sender, "Azienda " + pending.name + " eliminata.");
                }
                case "elimina-rifiuta" -> {
                    if (sender instanceof Player player && deleteConfirmations.remove(player.getUniqueId()) != null) msg(sender, "Eliminazione annullata; l'azienda non è stata eliminata.");
                    else msg(sender, "Non hai un'eliminazione da rifiutare.");
                }
                case "pex" -> {
                    if (args.length != 3) { msg(sender, "Uso: /aziendeadmin pex <nick> <azienda>"); return true; }
                    Company company = store.get(args[2]);
                    if (company == null) { msg(sender, "Azienda non trovata."); return true; }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    Company old = store.companyOf(target.getUniqueId());
                    if (old != null && old != company) old.employees.remove(target.getUniqueId());
                    company.director = target.getUniqueId(); company.directorName = target.getName() == null ? args[1] : target.getName();
                    company.employees.put(target.getUniqueId(), new Employment(company.directorName, "Direttore"));
                    store.save(); msg(sender, company.directorName + " è ora il direttore di " + company.name + ".");
                    if (target.isOnline()) msg(target.getPlayer(), "Sei il direttore dell'azienda " + company.name + ".");
                }
                case "info" -> {
                    if (args.length != 2) { msg(sender, "Uso: /aziendeadmin info <azienda>"); return true; }
                    Company company = store.get(args[1]); if (company == null) msg(sender, "Azienda non trovata."); else info(sender, company);
                }
                default -> msg(sender, "Uso: /aziendeadmin create <nome> | elimina <nome> | pex <nick> <azienda> | info <azienda>");
            }
            return true;
        }
        @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return starts(List.of("create", "elimina", "pex", "info"), args[0]);
            if (args.length == 2 && List.of("elimina", "info").contains(args[0].toLowerCase(Locale.ROOT))) return starts(store.names(), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("pex")) return starts(store.names(), args[2]);
            return List.of();
        }
    }

    private class CompanyCommands implements CommandExecutor, TabCompleter {
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { msg(sender, "Comando disponibile solo in gioco."); return true; }
            if (args.length == 0) { msg(sender, "Uso: /azienda <assumi|licenzia|info|rango> ..."); return true; }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "accetta", "rifiuta" -> {
                    Contract contract = contracts.get(player.getUniqueId());
                    if (contract == null) { msg(player, "Non hai contratti in attesa."); return true; }
                    contracts.remove(player.getUniqueId());
                    if (args[0].equalsIgnoreCase("rifiuta")) { msg(player, "Hai rifiutato il contratto di " + contract.company.name + "."); return true; }
                    if (store.get(contract.company.name) != contract.company || store.companyOf(player.getUniqueId()) != null) { msg(player, "Contratto non più valido."); return true; }
                    contract.company.employees.put(player.getUniqueId(), new Employment(player.getName(), contract.rank));
                    store.save(); msg(player, "Hai accettato il contratto come " + contract.rank + " in " + contract.company.name + ".");
                    Player employer = Bukkit.getPlayer(contract.employer);
                    if (employer != null) msg(employer, player.getName() + " ha accettato il contratto.");
                }
                case "assumi" -> {
                    Company company = actorCompany(player);
                    if (company == null) { msg(player, "Solo il direttore o il vicedirettore possono assumere."); return true; }
                    if (args.length != 3) { msg(player, "Uso: /azienda assumi <nick> <grado>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { msg(player, "Il candidato deve essere online e vicino."); return true; }
                    if (player.getWorld() != target.getWorld() || player.getLocation().distanceSquared(target.getLocation()) > 49) { msg(player, "Il candidato deve trovarsi entro 7 blocchi."); return true; }
                    if (target.getUniqueId().equals(company.director) || store.companyOf(target.getUniqueId()) != null) { msg(player, "Il giocatore è già direttore o dipendente di un'azienda."); return true; }
                    if (!company.ranks.contains(args[2].toLowerCase(Locale.ROOT))) { msg(player, "Grado inesistente. Gradi: " + String.join(", ", company.ranks)); return true; }
                    contracts.put(target.getUniqueId(), new Contract(company, args[2], player.getUniqueId()));
                    target.sendMessage(Component.text("Ti è stato inviato un contratto di lavoro come " + args[2] + " nell'azienda " + company.name + ". ", NamedTextColor.GOLD));
                    target.sendMessage(Component.text("[Accetta]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/azienda accetta"))
                            .append(Component.text("  ", NamedTextColor.GRAY))
                            .append(Component.text("[Rifiuta]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/azienda rifiuta"))));
                    msg(player, "Contratto inviato a " + target.getName() + ".");
                }
                case "licenzia" -> {
                    Company company = actorCompany(player);
                    if (company == null) { msg(player, "Solo il direttore o il vicedirettore possono licenziare."); return true; }
                    if (args.length != 2) { msg(player, "Uso: /azienda licenzia <nick>"); return true; }
                    UUID employeeId = company.employees.entrySet().stream().filter(e -> e.getValue().name.equalsIgnoreCase(args[1])).map(Map.Entry::getKey).findFirst().orElse(null);
                    if (employeeId == null) { msg(player, "Dipendente non trovato in questa azienda."); return true; }
                    if (employeeId.equals(company.director) || employeeId.equals(player.getUniqueId())) { msg(player, "Non puoi licenziare il direttore o te stesso."); return true; }
                    Employment dismissed = company.employees.remove(employeeId); contracts.remove(employeeId); store.save();
                    msg(player, dismissed.name + " è stato licenziato.");
                    Player online = Bukkit.getPlayer(employeeId); if (online != null) msg(online, "Sei stato licenziato dall'azienda " + company.name + ".");
                }
                case "info" -> {
                    Company company = store.companyOf(player.getUniqueId());
                    if (company == null) { msg(player, "Non fai parte di un'azienda."); return true; }
                    info(player, company);
                }
                case "rango" -> rankCommand(player, args);
                default -> msg(player, "Uso: /azienda <assumi|licenzia|info|rango> ...");
            }
            return true;
        }

        private void rankCommand(Player player, String[] args) {
            Company company = actorCompany(player);
            if (company == null) { msg(player, "Solo il direttore o il vicedirettore possono gestire i gradi."); return; }
            if (args.length < 3) { msg(player, "Uso: /azienda rango aggiungi <grado> | modifica <nick> <grado> | <grado> <nick>"); return; }
            if (args[1].equalsIgnoreCase("aggiungi")) {
                String rank = args[2];
                if (!rank.matches("[A-Za-z0-9_-]{1,24}")) { msg(player, "Il grado può contenere solo lettere, numeri, _ e -."); return; }
                if (!company.ranks.add(rank.toLowerCase(Locale.ROOT))) { msg(player, "Questo grado esiste già."); return; }
                store.save(); msg(player, "Grado " + rank + " aggiunto."); return;
            }
            String targetName, rank;
            if (args[1].equalsIgnoreCase("modifica") && args.length == 4) { targetName = args[2]; rank = args[3]; }
            else if (args.length == 3) { rank = args[1]; targetName = args[2]; }
            else { msg(player, "Uso: /azienda rango modifica <nick> <grado> oppure /azienda rango <grado> <nick>"); return; }
            Employment employee = company.employees.entrySet().stream().filter(e -> e.getValue().name.equalsIgnoreCase(targetName)).map(Map.Entry::getValue).findFirst().orElse(null);
            if (employee == null) { msg(player, "Dipendente non trovato."); return; }
            if (!company.ranks.contains(rank.toLowerCase(Locale.ROOT))) { msg(player, "Grado inesistente. Aggiungilo con /azienda rango aggiungi <grado>."); return; }
            employee.rank = rank; store.save(); msg(player, "Grado di " + employee.name + " modificato in " + rank + ".");
        }

        @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) return starts(List.of("assumi", "licenzia", "info", "rango", "accetta", "rifiuta"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("rango")) return starts(List.of("aggiungi", "modifica", "ViceDirettore"), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("rango")) {
                Company c = sender instanceof Player p ? actorCompany(p) : null;
                return c == null ? List.of() : starts(new ArrayList<>(c.ranks), args[2]);
            }
            return List.of();
        }
    }

    private static List<String> starts(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    private record Contract(Company company, String rank, UUID employer) { }
    private static final class Employment {
        final String name; String rank;
        Employment(String name, String rank) { this.name = name; this.rank = rank; }
    }
    private static final class Company {
        final String name; UUID director; String directorName = ""; double fund = 0;
        final Map<UUID, Employment> employees = new LinkedHashMap<>();
        final Set<String> ranks = new LinkedHashSet<>();
        Company(String name) { this.name = name; ranks.add("dipendente"); ranks.add("vicedirettore"); }
    }

    private static final class CompanyStore {
        private final AziendePlugin plugin;
        private final File file;
        private final Map<String, Company> companies = new LinkedHashMap<>();
        CompanyStore(AziendePlugin plugin) {
            this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "aziende.yml");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            load();
        }
        Company get(String name) { return companies.get(name.toLowerCase(Locale.ROOT)); }
        List<String> names() { return companies.values().stream().map(c -> c.name).toList(); }
        Company create(String name) { Company c = new Company(name); companies.put(name.toLowerCase(Locale.ROOT), c); save(); return c; }
        void delete(Company company) { companies.remove(company.name.toLowerCase(Locale.ROOT)); save(); }
        Company companyOf(UUID id) {
            for (Company c : companies.values()) if (c.employees.containsKey(id) || id.equals(c.director)) return c;
            return null;
        }
        void save() {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Company c : companies.values()) {
                String root = "companies." + c.name.toLowerCase(Locale.ROOT);
                yaml.set(root + ".name", c.name); yaml.set(root + ".director", c.director == null ? null : c.director.toString());
                yaml.set(root + ".directorName", c.directorName); yaml.set(root + ".fund", c.fund);
                yaml.set(root + ".ranks", new ArrayList<>(c.ranks));
                for (Map.Entry<UUID, Employment> e : c.employees.entrySet()) {
                    yaml.set(root + ".employees." + e.getKey() + ".name", e.getValue().name);
                    yaml.set(root + ".employees." + e.getKey() + ".rank", e.getValue().rank);
                }
            }
            try { yaml.save(file); } catch (IOException e) { plugin.getLogger().severe("Impossibile salvare aziende.yml: " + e.getMessage()); }
        }
        void load() {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("companies"); if (section == null) return;
            for (String key : section.getKeys(false)) {
                ConfigurationSection data = section.getConfigurationSection(key); if (data == null) continue;
                String name = data.getString("name", key); Company c = new Company(name);
                String director = data.getString("director");
                try { if (director != null) c.director = UUID.fromString(director); } catch (IllegalArgumentException ignored) { }
                c.directorName = data.getString("directorName", ""); c.fund = data.getDouble("fund", 0);
                List<String> ranks = data.getStringList("ranks"); if (!ranks.isEmpty()) { c.ranks.clear(); c.ranks.addAll(ranks); }
                ConfigurationSection employees = data.getConfigurationSection("employees");
                if (employees != null) for (String id : employees.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(id); ConfigurationSection employee = employees.getConfigurationSection(id);
                        if (employee != null) c.employees.put(uuid, new Employment(employee.getString("name", "Sconosciuto"), employee.getString("rank", "Dipendente")));
                    } catch (IllegalArgumentException ignored) { }
                }
                companies.put(name.toLowerCase(Locale.ROOT), c);
            }
        }
    }
}
