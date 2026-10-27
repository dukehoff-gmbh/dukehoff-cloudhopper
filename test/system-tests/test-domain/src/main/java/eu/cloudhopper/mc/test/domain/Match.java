package eu.cloudhopper.mc.test.domain;

/*-
 * #%L
 * test-domain - a library from the "Cloudhopper" project.
 * 
 * Dukehoff GmbH designates this particular file as subject to the "Classpath"
 * exception as provided in the LICENSE-EXCEPTIONS file that accompanies this code.
 * %%
 * Copyright (C) 2024 - 2026 Dukehoff GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


public class Match {
    private int tournamentId;
    private int matchId;
    private String player1;
    private String player2;
    private String status;

    public Match() {}

    public Match(int tournamentId, int matchId, String player1, String player2, String status) {
        this.tournamentId = tournamentId;
        this.matchId = matchId;
        this.player1 = player1;
        this.player2 = player2;
        this.status = status;
    }

    public int getTournamentId() { return tournamentId; }
    public int getMatchId() { return matchId; }
    public String getPlayer1() { return player1; }
    public String getPlayer2() { return player2; }
    public String getStatus() { return status; }

    public void setTournamentId(int tournamentId) { this.tournamentId = tournamentId; }
    public void setMatchId(int matchId) { this.matchId = matchId; }
    public void setPlayer1(String player1) { this.player1 = player1; }
    public void setPlayer2(String player2) { this.player2 = player2; }
    public void setStatus(String status) { this.status = status; }
}
