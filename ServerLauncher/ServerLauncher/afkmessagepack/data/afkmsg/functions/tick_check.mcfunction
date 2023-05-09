scoreboard players add server ticks_wo_players 1
execute if entity @a run function afkmsg:score_reset
execute if score server ticks_wo_players matches 72000 run say stopnow:1234