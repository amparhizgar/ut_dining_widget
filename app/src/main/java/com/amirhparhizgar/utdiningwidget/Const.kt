package com.amirhparhizgar.utdiningwidget

/**
 * Created by AmirHossein Parhizgar on 1/11/2023.
 */

const val getRest2FuncDef = "function getRest2(group) {\n" +
        "\n" +
        "\t\$.ajax({\n" +
        "\t\ttype: \"post\",\n" +
        "\t\turl: \"/Reserves/GetRestaurantByPersonGroup\",\n" +
        "\t\tdata: { personGroupId: group },\n" +
        "\t\tsuccess: function (data) {\n" +
        "\t\t\tvar response = JSON.stringify(data);\n" +
        "\t\t\tconsole.log(response);\n" +
        "\t\t\tbridge.setRestaurantsForGroup(group, response);\n" +
        "\t\t},\n" +
        "\t\terror: function () {\n" +
        "\t\t\tconsole.log(\"Error\")\n" +
        "\t\t\tbridge.putJsResult(\"\");\n" +
        "\t\t}\n" +
        "\t});\n" +
        "}"

const val getReservePage2FuncDef = "function getReservePage2(groupId, restId) {\n" +
        "\tif (restId == null) restId = 0;\n" +
        "\tvar isKiosk = false;\n" +
        "\n" +
        "\t\$.ajax({\n" +
        "\t\turl: \"/Reserves/GetReservePage\", // SetPersonGroup\",\n" +
        "\t\ttype: \"post\",\n" +
        "\t\tdataType: \"html\",\n" +
        "\t\tdata: { personId: 0, personGroupId: groupId, restId: restId, isKiosk: isKiosk },\n" +
        "\t\tsuccess: function (data) {\n" +
        "\t\t\tvar response = JSON.stringify(data);\n" +
        "\t\t\tbridge.setReserves(groupId, restId, data);\n" +
        "\t\t},\n" +
        "\t\terror: function () {\n" +
        "\t\t\tconsole.log(\"Error\")\n" +
        "\t\t\tbridge.putJsResult(\"\");\n" +
        "\t\t}\n" +
        "\t});\n" +
        "}"

const val getNextWeek2FuncDef = "function getNextWeek2(groupId, restId) {\n" +
        "\tif (restId == null) restId = 0;\n" +
        "\tvar isKiosk = false;\n" +
        "\n" +
        "\t\$.ajax({\n" +
        "\t\turl: \"/Reserves/GetNextReservePage\", // SetPersonGroup\",\n" +
        "\t\ttype: \"post\",\n" +
        "\t\tdataType: \"html\",\n" +
        "\t\tdata: { personId: 0, personGroupId: groupId, restId: restId, isKiosk: isKiosk },\n" +
        "\t\tsuccess: function (data) {\n" +
        "\t\t\tbridge.setReserves(groupId, restId, data);\n" +
        "\t\t},\n" +
        "\t\terror: function () {\n" +
        "\t\t\tconsole.log(\"Error\")\n" +
        "\t\t\tbridge.putJsResult(\"\");\n" +
        "\t\t}\n" +
        "\t});\n" +
        "}"