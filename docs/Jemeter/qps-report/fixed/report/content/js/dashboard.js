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

    var data = {"OkPercent": 90.26865585084019, "KoPercent": 9.731344149159817};
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
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.9025923187334174, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "01 标的列表"], "isController": false}, {"data": [1.0, 500, 1500, "02 标的详情"], "isController": false}, {"data": [1.0, 500, 1500, "登录-投资人"], "isController": false}, {"data": [1.0, 500, 1500, "登录-管理员"], "isController": false}, {"data": [0.9992177314211212, 500, 1500, "05 账户信息"], "isController": false}, {"data": [1.0, 500, 1500, "登录-借款人"], "isController": false}, {"data": [1.0, 500, 1500, "08 我的投资（投资人身份）"], "isController": false}, {"data": [1.0, 500, 1500, "04 消息列表"], "isController": false}, {"data": [0.1228437009932044, 500, 1500, "09 管理端标的列表"], "isController": false}, {"data": [0.9999348194498762, 500, 1500, "03 推荐标的"], "isController": false}, {"data": [1.0, 500, 1500, "06 我的合同列表"], "isController": false}, {"data": [1.0, 500, 1500, "07 交易流水"], "isController": false}]}, function(index, item){
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
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 68973, 6712, 9.731344149159817, 33.28409667551032, 1, 601, 8.0, 163.0, 220.0, 286.0, 560.5920218472642, 1389.9712596821255, 220.58282865380863], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["01 标的列表", 7672, 0, 0.0, 16.465980187695557, 4, 496, 14.0, 28.0, 35.0, 56.0, 63.93173503995733, 257.9556604834879, 23.100333949984584], "isController": false}, {"data": ["02 标的详情", 7671, 0, 0.0, 12.040281579976552, 2, 492, 10.0, 21.0, 27.0, 48.279999999999745, 63.92872917586859, 24.39140492830832, 21.538487857104165], "isController": false}, {"data": ["登录-投资人", 1, 0, 0.0, 181.0, 181, 181, 181.0, 181.0, 181.0, 181.0, 5.524861878453039, 3.037594958563536, 1.2247496546961327], "isController": false}, {"data": ["登录-管理员", 1, 0, 0.0, 271.0, 271, 271, 271.0, 271.0, 271.0, 271.0, 3.6900369003690034, 2.407172509225092, 0.8107991236162361], "isController": false}, {"data": ["05 账户信息", 7670, 0, 0.0, 178.41629726205966, 75, 601, 177.0, 238.0, 263.0, 370.28999999999996, 63.86132018916939, 37.961159573161595, 22.575974519999335], "isController": false}, {"data": ["登录-借款人", 1, 0, 0.0, 220.0, 220, 220, 220.0, 220.0, 220.0, 220.0, 4.545454545454545, 2.499112215909091, 1.0076349431818181], "isController": false}, {"data": ["08 我的投资（投资人身份）", 7653, 0, 0.0, 15.295309029138929, 4, 145, 13.0, 26.0, 34.0, 57.0, 63.86600906291465, 114.11970880118753, 22.265786362754426], "isController": false}, {"data": ["04 消息列表", 7671, 0, 0.0, 15.252379090079476, 3, 490, 12.0, 25.0, 32.399999999999636, 55.0, 63.936188833045776, 147.27161400380484, 22.04050259576676], "isController": false}, {"data": ["09 管理端标的列表", 7652, 6712, 87.71562990067956, 7.719027705175147, 1, 479, 5.0, 17.0, 22.0, 36.0, 63.884853645911605, 80.08248691068059, 47.539314920102186], "isController": false}, {"data": ["03 推荐标的", 7671, 0, 0.0, 20.616347281971017, 4, 509, 17.0, 36.0, 45.0, 70.27999999999975, 63.93192595864552, 269.0071616482411, 22.351200676948338], "isController": false}, {"data": ["06 我的合同列表", 7655, 0, 0.0, 17.615284128020903, 5, 495, 14.0, 28.0, 36.0, 64.0, 63.870440209591834, 425.49342757108764, 22.26733120588309], "isController": false}, {"data": ["07 交易流水", 7655, 0, 0.0, 15.908033964728887, 3, 498, 13.0, 27.0, 35.0, 57.4399999999996, 63.8709731249635, 69.77948954587363, 22.64176098082202], "isController": false}]}, function(index, item){
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
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": [{"data": ["401", 6712, 100.0, 9.731344149159817], "isController": false}]}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 68973, 6712, "401", 6712, "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": ["09 管理端标的列表", 7652, 6712, "401", 6712, "", "", "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
