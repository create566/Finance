/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 86.85278219395866, "KoPercent": 13.147217806041336};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.8685278219395867, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.8263519396343233, 500, 1500, "01 标的列表"], "isController": false}, {"data": [1.0, 500, 1500, "02 标的详情"], "isController": false}, {"data": [1.0, 500, 1500, "登录-投资人"], "isController": false}, {"data": [1.0, 500, 1500, "登录-管理员"], "isController": false}, {"data": [1.0, 500, 1500, "05 账户信息"], "isController": false}, {"data": [1.0, 500, 1500, "登录-借款人"], "isController": false}, {"data": [1.0, 500, 1500, "08 我的投资（投资人身份）"], "isController": false}, {"data": [1.0, 500, 1500, "04 消息列表"], "isController": false}, {"data": [0.0, 500, 1500, "09 管理端标的列表"], "isController": false}, {"data": [1.0, 500, 1500, "03 推荐标的"], "isController": false}, {"data": [0.9980098337625849, 500, 1500, "06 我的合同列表"], "isController": false}, {"data": [1.0, 500, 1500, "07 交易流水"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 78625, 10337, 13.147217806041336, 29.179281399046232, 1, 466, 6.0, 154.90000000000146, 217.0, 239.0, 649.9384160101841, 1519.179302277161, 248.10925171577543], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["01 标的列表", 10337, 1795, 17.36480603656767, 6.817258392183417, 1, 103, 6.0, 10.0, 12.0, 19.0, 86.13233566363644, 293.02079514302613, 31.122035347212385], "isController": false}, {"data": ["02 标的详情", 8542, 0, 0.0, 5.112502926715031, 2, 327, 5.0, 7.0, 9.0, 14.0, 71.46382886161517, 27.266134159910983, 24.07716890357152], "isController": false}, {"data": ["登录-投资人", 1, 0, 0.0, 96.0, 96, 96, 96.0, 96.0, 96.0, 96.0, 10.416666666666666, 5.727132161458333, 2.3091634114583335], "isController": false}, {"data": ["登录-管理员", 1, 0, 0.0, 121.0, 121, 121, 121.0, 121.0, 121.0, 121.0, 8.264462809917356, 5.391270661157025, 1.8159220041322315], "isController": false}, {"data": ["05 账户信息", 8542, 0, 0.0, 212.71704518847997, 66, 466, 217.0, 243.0, 254.0, 287.0, 71.37008505589625, 42.42628236594088, 25.230440224838325], "isController": false}, {"data": ["登录-借款人", 1, 0, 0.0, 95.0, 95, 95, 95.0, 95.0, 95.0, 95.0, 10.526315789473683, 5.787417763157895, 2.333470394736842], "isController": false}, {"data": ["08 我的投资（投资人身份）", 8525, 0, 0.0, 7.094310850439871, 2, 157, 6.0, 10.0, 12.0, 20.0, 71.41780041552174, 127.61244049389285, 24.898588621427017], "isController": false}, {"data": ["04 消息列表", 8542, 0, 0.0, 6.817958323577629, 2, 144, 6.0, 10.0, 11.0, 18.56999999999971, 71.47279816590525, 164.63228811059793, 24.63857202398882], "isController": false}, {"data": ["09 管理端标的列表", 8525, 8525, 100.0, 3.9785337243401755, 1, 323, 3.0, 6.0, 7.0, 12.0, 71.42079203773363, 27.74030491546794, 45.89343863362181], "isController": false}, {"data": ["03 推荐标的", 8542, 0, 0.0, 8.282720674315135, 3, 113, 7.0, 12.0, 14.0, 25.0, 71.4674163131782, 300.71483201716404, 24.985678750115042], "isController": false}, {"data": ["06 我的合同列表", 8542, 17, 0.19901662374151252, 9.135799578553081, 2, 315, 8.0, 12.0, 14.0, 25.0, 71.42737687097583, 474.94654588071745, 24.901927288025753], "isController": false}, {"data": ["07 交易流水", 8525, 0, 0.0, 7.192375366568904, 2, 116, 7.0, 10.0, 12.0, 20.0, 71.41660383680993, 78.02318781676719, 25.31662811793164], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": [{"data": ["401", 1814, 17.54861178291574, 2.307154213036566], "isController": false}, {"data": ["403", 8523, 82.45138821708426, 10.84006359300477], "isController": false}]}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 78625, 10337, "403", 8523, "401", 1814, "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": ["01 标的列表", 10337, 1795, "401", 1795, "", "", "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": ["09 管理端标的列表", 8525, 8525, "403", 8523, "401", 2, "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}, {"data": ["06 我的合同列表", 8542, 17, "401", 17, "", "", "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
