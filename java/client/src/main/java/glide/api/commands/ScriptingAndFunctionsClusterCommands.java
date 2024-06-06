/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for a cluster client.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsClusterCommands {

    /**
     * Loads a library to Redis.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoad(code, true).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace);

    /**
     * Loads a library to Redis.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.functionLoad(code, true, route).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace, Route route);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList(true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(boolean withCode);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList("myLib?_backup", true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList(true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            boolean withCode, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList("myLib?_backup", true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            String libNamePattern, boolean withCode, Route route);

    /**
     * Deletes a library and all its functions.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-delete/">redis.io</a> for details.
     * @param libName The library name to delete.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName);

    /**
     * Deletes a library and all its functions.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-delete/">redis.io</a> for details.
     * @param libName The library name to delete.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib", RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName, Route route);
}
